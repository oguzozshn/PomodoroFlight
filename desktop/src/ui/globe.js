/**
 * Ortografik küre: kara parçaları, paralel/meridyen ağı, gündüz/gece sınırı,
 * büyük daire rotası, uçuş izleri ve uçak.
 *
 * Android sürümündeki ui/Globe.kt + ui/GlobeState.kt karşılığı. Kamera
 * doğu/kuzey/yukarı birim vektörlerinden kuruluyor; bir noktayı izdüşürmek için
 * trigonometri değil yalnızca iç çarpım gerekiyor.
 */

import { RAD, DEG, wrapLon } from "../core/geo.js";
import { samplePath } from "../core/route.js";

export const GLOBE_MIN_ZOOM = 0.55;
export const GLOBE_MAX_ZOOM = 18;
export const GLOBE_FIT_ZOOM = 1;

/** Bu yakınlaştırmadan sonra ince kıyı şeridine geçilir. */
const FINE_DETAIL_ZOOM = 2.2;

/** Aynı anda kürede en fazla kaç yer işaretlenir. */
const MAX_MARKERS = 140;
/** Bunların kaçının adı yazılır. */
const MAX_LABELS = 22;

const ROUTE_STEPS = 240;
const TERMINATOR_STEPS = 180;
const LIMB_STEPS = 96;

const TOKENS = [
  "space", "globe-a", "globe-b", "grat", "limb", "amber", "teal",
  "ink", "muted", "panel", "land", "land-line", "lake", "night"
];

/**
 * Kameranın bakış yönünden türetilen taban: doğu, kuzey ve yukarı birim
 * vektörleri.
 */
function createProjection(centerLat, centerLon, cx, cy, r) {
  const phi = centerLat * RAD, lambda = centerLon * RAD;
  const sp = Math.sin(phi), cp = Math.cos(phi);
  const sl = Math.sin(lambda), cl = Math.cos(lambda);

  return {
    centerLat, centerLon, cx, cy, r,
    eastX: -sl, eastY: cl, eastZ: 0,
    northX: -sp * cl, northY: -sp * sl, northZ: cp,
    upX: cp * cl, upY: cp * sl, upZ: sp,

    screenX(x, y, z) { return cx + r * (x * this.eastX + y * this.eastY + z * this.eastZ); },
    screenY(x, y, z) { return cy - r * (x * this.northX + y * this.northY + z * this.northZ); },
    visible(x, y, z) { return x * this.upX + y * this.upY + z * this.upZ >= 0; },

    at(lat, lon) {
      const p = lat * RAD, l = lon * RAD, c = Math.cos(p);
      const x = c * Math.cos(l), y = c * Math.sin(l), z = Math.sin(p);
      return { x: this.screenX(x, y, z), y: this.screenY(x, y, z), vis: this.visible(x, y, z) };
    },

    /** Ekran noktasının altındaki coğrafi koordinat; küre dışıysa null. */
    locate(px, py, radiusScale = 1) {
      const rad = r * radiusScale;
      if (rad <= 0) return null;
      const dx = (px - cx) / rad, dy = (cy - py) / rad;
      const rho = Math.hypot(dx, dy);
      if (rho > 1) return null;
      if (rho < 1e-4) return [centerLat, centerLon];

      const c = Math.asin(Math.max(-1, Math.min(1, rho)));
      const phi0 = centerLat * RAD;
      const lat = Math.asin(Math.max(-1, Math.min(1,
        Math.cos(c) * Math.sin(phi0) + (dy * Math.sin(c) * Math.cos(phi0)) / rho)));
      const lon = centerLon * RAD + Math.atan2(
        dx * Math.sin(c),
        rho * Math.cos(c) * Math.cos(phi0) - dy * Math.sin(c) * Math.sin(phi0)
      );
      return [lat * DEG, lon * DEG];
    },

    /**
     * anchor koordinatını, ölçek radiusScale katına çıktığında hâlâ (px, py)
     * pikselinde tutan kamera merkezini çözer. Böyle bir kamera yoksa null.
     *
     * Kamera tabanında nokta (doğu, kuzey, yukarı) = (a, d, s) bileşenlerine
     * sahiptir; buradan enlem tek bir sinüs denklemiyle, boylam da yatay
     * bileşenin dönme açısıyla çözülür.
     */
    solveCamera(anchor, px, py, radiusScale) {
      const rad = r * radiusScale;
      if (rad <= 0) return null;
      const a = (px - cx) / rad, d = (cy - py) / rad;
      const rho2 = a * a + d * d;
      if (rho2 > 1) return null;
      const s = Math.sqrt(1 - rho2);

      const p = anchor[0] * RAD, l = anchor[1] * RAD, cpp = Math.cos(p);
      const ax = cpp * Math.cos(l), ay = cpp * Math.sin(l), az = Math.sin(p);

      const rr = Math.hypot(s, d);
      if (rr < 1e-9 || Math.abs(az) > rr) return null;

      const phi0 = Math.asin(Math.max(-1, Math.min(1, az / rr))) - Math.atan2(d, s);
      const b = s * Math.cos(phi0) - d * Math.sin(phi0);
      if (Math.abs(a) < 1e-12 && Math.abs(b) < 1e-12) return null;
      const lambda0 = Math.atan2(ay, ax) - Math.atan2(a, b);
      return [phi0 * DEG, lambda0 * DEG];
    }
  };
}

export function createGlobe(canvas) {
  const ctx = canvas.getContext("2d");

  /** Kullanıcının sürdüğü kamera. */
  const view = {
    lat: 0, lon: 0, zoom: GLOBE_FIT_ZOOM,
    tLat: 0, tLon: 0,          // rota merkezine yumuşak dönüş hedefi
    untouched: true
  };

  let vLat = 0, vLon = 0, lastRotate = 0;   // atalet
  let W = 0, H = 0, C = {};
  let proj = null;
  let markers = [];                          // son karenin işaretçileri

  /* ---------------- kamera ---------------- */

  function aim(lat, lon, snap) {
    view.tLat = lat;
    view.tLon = wrapLon(lon);
    if (snap) { view.lat = view.tLat; view.lon = view.tLon; view.untouched = true; }
  }

  /** Kamerayı doğrudan verilen noktaya taşır (sabitlemeli yakınlaştırma için). */
  function moveTo(lat, lon) {
    view.untouched = false;
    view.lat = view.tLat = Math.max(-89, Math.min(89, lat));
    view.lon = view.tLon = wrapLon(lon);
  }

  function rotateBy(dLat, dLon) {
    view.untouched = false;
    // Bırakınca dönmeye devam etmesi için anlık hız ölçülür (derece/saniye).
    const now = performance.now();
    const elapsed = (now - lastRotate) / 1000;
    if (lastRotate !== 0 && elapsed > 0.001 && elapsed < 0.12) {
      vLat = 0.6 * vLat + 0.4 * (dLat / elapsed);
      vLon = 0.6 * vLon + 0.4 * (dLon / elapsed);
    }
    lastRotate = now;
    view.lat = view.tLat = Math.max(-89, Math.min(89, view.lat + dLat));
    view.lon = view.tLon = wrapLon(view.lon + dLon);
  }

  /** Piksel farkını dereceye çevirir; yakınlaştıkça hassaslaşır. */
  function rotateByPixels(dx, dy) {
    const k = 105 / Math.max(60, proj ? proj.r : 300);
    rotateBy(dy * k, -dx * k);
  }

  function stopMomentum() { vLat = 0; vLon = 0; lastRotate = 0; }

  /** Parmak/fare kalkınca küre sönümlenerek dönmeye devam eder. */
  function releaseMomentum() {
    if (Math.abs(vLat) < 12 && Math.abs(vLon) < 12) stopMomentum();
    lastRotate = 0;
  }

  function zoomBy(factor) {
    if (factor === 1) return 1;
    view.untouched = false;
    const before = view.zoom;
    view.zoom = Math.max(GLOBE_MIN_ZOOM, Math.min(GLOBE_MAX_ZOOM, view.zoom * factor));
    return view.zoom / before;
  }

  const setZoom = value => {
    view.untouched = false;
    view.zoom = Math.max(GLOBE_MIN_ZOOM, Math.min(GLOBE_MAX_ZOOM, value));
  };

  /**
   * Yakınlaştırmayı ekranın ortasına değil, imlecin altındaki noktaya sabitler:
   * önce orada hangi koordinatın durduğu bulunur, sonra yeni ölçekte aynı
   * koordinatı aynı piksele getiren kamera doğrudan çözülür.
   */
  function zoomAnchored(px, py, factor) {
    if (factor === 1 || !proj) return;
    const anchor = proj.locate(px, py);
    const applied = zoomBy(factor);
    if (applied === 1 || !anchor) return;
    // Çözüm yoksa (uzaklaşırken nokta kürenin dışına düşüyorsa) merkeze göre
    // yakınlaştırmakla yetinilir.
    const camera = proj.solveCamera(anchor, px, py, applied);
    if (camera) moveTo(camera[0], camera[1]);
  }

  const resetView = () => { view.zoom = GLOBE_FIT_ZOOM; view.untouched = true; stopMomentum(); };

  /** Her karede hedefe bir adım yaklaşır ve ataleti söndürür. */
  function ease(dt = 1 / 60) {
    if (Math.abs(vLat) > 3 || Math.abs(vLon) > 3) {
      const step = Math.max(0, Math.min(0.05, dt));
      view.lat = view.tLat = Math.max(-89, Math.min(89, view.lat + vLat * step));
      view.lon = view.tLon = wrapLon(view.lon + vLon * step);
      const decay = Math.exp(-3.4 * step);
      vLat *= decay; vLon *= decay;
      return;
    }
    const dLat = view.tLat - view.lat, dLon = wrapLon(view.tLon - view.lon);
    if (Math.abs(dLat) < 0.02 && Math.abs(dLon) < 0.02) {
      view.lat = view.tLat; view.lon = view.tLon;
      return;
    }
    view.lat += dLat * 0.14;
    view.lon = wrapLon(view.lon + dLon * 0.14);
  }

  /* ---------------- tuval ---------------- */

  function readTokens() {
    const s = getComputedStyle(document.documentElement);
    C = {};
    for (const k of TOKENS) C[k] = s.getPropertyValue("--" + k).trim();
  }

  /** @param superSample tuvalin arka belleğini bu katına çıkarır (kalite ayarı). */
  function resize(superSample = 1) {
    const r = canvas.getBoundingClientRect();
    if (!r.width) return;
    const dpr = Math.min(window.devicePixelRatio || 1, 2) * superSample;
    W = r.width; H = r.height;
    canvas.width = Math.round(W * dpr);
    canvas.height = Math.round(H * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  /**
   * Ekran koordinatının altındaki havalimanının kodu; son karenin
   * işaretçilerinden. Kod döner, nesne değil: çağıranlar kodu vurgu kümesinde
   * ve rota anahtarında kullanıyor.
   */
  function placeAt(px, py, reach = 15) {
    let best = null, bd = reach * reach;
    for (const m of markers) {
      const d = (m.x - px) ** 2 + (m.y - py) ** 2;
      if (d <= bd) { bd = d; best = m.place.code; }
    }
    return best;
  }

  /* ---------------- çizim yardımcıları ---------------- */

  function strokePolyline(points) {
    let drawing = false;
    ctx.beginPath();
    for (const [lat, lon] of points) {
      const q = proj.at(lat, lon);
      if (!q.vis) { drawing = false; continue; }
      if (drawing) ctx.lineTo(q.x, q.y);
      else { ctx.moveTo(q.x, q.y); drawing = true; }
    }
    ctx.stroke();
  }

  /**
   * Kara parçalarını doldurulabilir tek bir yola toplar. Kürenin arkasına düşen
   * noktalar atılmaz, bulundukları yönde ufka **bastırılır**; aradaki boşluk da
   * ufuk boyunca doldurulur. Kırpma denenseydi halkanın iki ucunu birleştiren
   * kiriş, ağzı ufka açılan körfezleri baştan başa kara yapardı.
   */
  function fillLand(shapes, color) {
    const TWO_PI = Math.PI * 2;
    ctx.beginPath();
    for (const shape of shapes) {
      if (shape.isHidden(proj.upX, proj.upY, proj.upZ)) continue;
      const xyz = shape.xyz;
      let started = false, clamped = NaN;

      for (let i = 0; i < xyz.length; i += 3) {
        const x = xyz[i], y = xyz[i + 1], z = xyz[i + 2];
        const east = x * proj.eastX + y * proj.eastY + z * proj.eastZ;
        const north = x * proj.northX + y * proj.northY + z * proj.northZ;

        if (proj.visible(x, y, z)) {
          const sx = proj.cx + proj.r * east, sy = proj.cy - proj.r * north;
          if (started) ctx.lineTo(sx, sy); else { ctx.moveTo(sx, sy); started = true; }
          clamped = NaN;
        } else if (Math.hypot(east, north) > 1e-6) {
          const angle = Math.atan2(north, east);
          if (started && !Number.isNaN(clamped)) {
            let delta = angle - clamped;
            while (delta > Math.PI) delta -= TWO_PI;
            while (delta < -Math.PI) delta += TWO_PI;
            const steps = Math.floor(Math.abs(delta) / 0.08);
            for (let k = 1; k < steps; k++) {
              const between = clamped + (delta * k) / steps;
              ctx.lineTo(proj.cx + proj.r * Math.cos(between), proj.cy - proj.r * Math.sin(between));
            }
          }
          const sx = proj.cx + proj.r * Math.cos(angle), sy = proj.cy - proj.r * Math.sin(angle);
          if (started) ctx.lineTo(sx, sy); else { ctx.moveTo(sx, sy); started = true; }
          clamped = angle;
        }
      }
      if (started) ctx.closePath();
    }
    ctx.fillStyle = color;
    ctx.fill("evenodd");   // delikler (iç denizler) kendiliğinden açılır
  }

  /** Kıyı çizgisi: yalnızca görünen parçalar, ufuk boyunca çizgi çizilmez. */
  function strokeCoast(shapes, color, width) {
    ctx.beginPath();
    for (const shape of shapes) {
      if (shape.isHidden(proj.upX, proj.upY, proj.upZ)) continue;
      const xyz = shape.xyz;
      let drawing = false;
      for (let i = 0; i < xyz.length; i += 3) {
        const x = xyz[i], y = xyz[i + 1], z = xyz[i + 2];
        if (!proj.visible(x, y, z)) { drawing = false; continue; }
        const sx = proj.screenX(x, y, z), sy = proj.screenY(x, y, z);
        if (drawing) ctx.lineTo(sx, sy); else { ctx.moveTo(sx, sy); drawing = true; }
      }
    }
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.stroke();
  }

  /**
   * Dünyanın gece yarısını gölgeler. Gece bölgesinin sınırı iki yaydan oluşur:
   * terminatörün görünen parçası ve onu kapatan ufuk yayı. Ufuk yayı kürenin
   * dışına biraz taşırılır; kırpma zaten daireye yapıldığı için kenarda ince
   * bir şerit kalmaz.
   */
  function drawNight(sun) {
    const p = sun[0] * RAD, l = sun[1] * RAD, cp = Math.cos(p);
    const sx = cp * Math.cos(l), sy = cp * Math.sin(l), sz = Math.sin(p);

    const sunUp = sx * proj.upX + sy * proj.upY + sz * proj.upZ;
    if (sunUp >= 0.99999) return;
    if (sunUp <= -0.99999) {
      ctx.beginPath(); ctx.arc(proj.cx, proj.cy, proj.r, 0, Math.PI * 2);
      ctx.fillStyle = C.night; ctx.fill();
      return;
    }

    // terminatör düzlemini geren iki dik birim vektör
    const ref = Math.abs(sz) < 0.9 ? [0, 0, 1] : [1, 0, 0];
    let ax = sy * ref[2] - sz * ref[1];
    let ay = sz * ref[0] - sx * ref[2];
    let az = sx * ref[1] - sy * ref[0];
    let len = Math.hypot(ax, ay, az);
    if (len < 1e-6) return;
    ax /= len; ay /= len; az /= len;

    let bx = sy * az - sz * ay;
    let by = sz * ax - sx * az;
    let bz = sx * ay - sy * ax;
    len = Math.hypot(bx, by, bz);
    if (len < 1e-6) return;
    bx /= len; by /= len; bz /= len;

    const point = i => {
      const t = (2 * Math.PI * i) / TERMINATOR_STEPS;
      const c = Math.cos(t), s = Math.sin(t);
      return [ax * c + bx * s, ay * c + by * s, az * c + bz * s];
    };

    const vis = new Array(TERMINATOR_STEPS);
    for (let i = 0; i < TERMINATOR_STEPS; i++) vis[i] = proj.visible(...point(i));

    let start = -1;
    for (let i = 0; i < TERMINATOR_STEPS; i++) {
      const prev = (i - 1 + TERMINATOR_STEPS) % TERMINATOR_STEPS;
      if (vis[i] && !vis[prev]) { start = i; break; }
    }
    if (start < 0) {
      if (sunUp < 0) {
        ctx.beginPath(); ctx.arc(proj.cx, proj.cy, proj.r, 0, Math.PI * 2);
        ctx.fillStyle = C.night; ctx.fill();
      }
      return;
    }

    ctx.beginPath();
    let first = null, last = null, count = 0, i = start;
    while (vis[i % TERMINATOR_STEPS] && count <= TERMINATOR_STEPS) {
      const v = point(i % TERMINATOR_STEPS);
      const px = proj.screenX(...v), py = proj.screenY(...v);
      if (count === 0) { ctx.moveTo(px, py); first = v; } else ctx.lineTo(px, py);
      last = v;
      count++; i++;
    }
    if (count < 2) return;

    // ufuk yayı: son noktadan ilk noktaya, gece tarafından dolaşarak
    const angleOf = v => Math.atan2(
      v[0] * proj.northX + v[1] * proj.northY + v[2] * proj.northZ,
      v[0] * proj.eastX + v[1] * proj.eastY + v[2] * proj.eastZ
    );
    const angleFirst = angleOf(first), angleLast = angleOf(last);
    let sweep = (((angleFirst - angleLast) % (2 * Math.PI)) + 2 * Math.PI) % (2 * Math.PI);
    const mid = angleLast + sweep / 2;
    const midX = proj.eastX * Math.cos(mid) + proj.northX * Math.sin(mid);
    const midY = proj.eastY * Math.cos(mid) + proj.northY * Math.sin(mid);
    const midZ = proj.eastZ * Math.cos(mid) + proj.northZ * Math.sin(mid);
    if (midX * sx + midY * sy + midZ * sz > 0) sweep -= 2 * Math.PI;

    const outer = proj.r * 1.02;
    for (let k = 1; k <= LIMB_STEPS; k++) {
      const angle = angleLast + (sweep * k) / LIMB_STEPS;
      ctx.lineTo(proj.cx + outer * Math.cos(angle), proj.cy - outer * Math.sin(angle));
    }
    ctx.closePath();
    ctx.fillStyle = C.night;
    ctx.fill();
  }

  /**
   * Yer işaretçileri. Önce ekrana düşen tüm adaylar toplanır; bütçe sonra
   * ekranın ortasına en yakın olanlara dağıtılır. Aksi hâlde bütçe veri
   * setinin sırasına göre harcanır ve baktığınız şehir işaretlenmeden kalır.
   */
  function drawPlaces(places, highlighted, zoom) {
    const maxRank = zoom < 1.7 ? 0 : zoom < 3 ? 1 : 2;
    const midX = W / 2, midY = H / 2;
    const candidates = [];

    for (const place of places) {
      const chosen = highlighted.has(place.code);
      if (!chosen && place.rank > maxRank) continue;
      const q = proj.at(place.lat, place.lon);
      if (!q.vis || q.x < 0 || q.y < 0 || q.x > W || q.y > H) continue;
      candidates.push({ x: q.x, y: q.y, place, chosen, d2: (q.x - midX) ** 2 + (q.y - midY) ** 2 });
    }
    candidates.sort((a, b) =>
      (a.chosen ? 0 : 1) - (b.chosen ? 0 : 1) || a.place.rank - b.place.rank || a.d2 - b.d2);

    const shown = [];
    for (const c of candidates) {
      if (!c.chosen && shown.length >= MAX_MARKERS) break;
      ctx.beginPath();
      ctx.arc(c.x, c.y, c.chosen ? 3.8 : 2.4, 0, Math.PI * 2);
      ctx.fillStyle = c.chosen ? C.amber : C.teal;
      ctx.fill();
      if (c.chosen) {
        ctx.beginPath();
        ctx.arc(c.x, c.y, 7.6, 0, Math.PI * 2);
        ctx.strokeStyle = C.amber;
        ctx.globalAlpha = 0.35;
        ctx.lineWidth = 1.4;
        ctx.stroke();
        ctx.globalAlpha = 1;
      }
      shown.push(c);
    }

    // Adlar yalnızca ilk birkaç yer için ve üst üste binmeden. Bütçe tek başına
    // yetmiyor: kalabalık bölgelerde yirmi ad aynı yere yığılıp okunmaz oluyor.
    let labelled = 0;
    const taken = [];
    const overlaps = (x, y, w, h) => taken.some(t =>
      x < t.x + t.w + 3 && x + w + 3 > t.x && y < t.y + t.h + 2 && y + h + 2 > t.y);

    ctx.textAlign = "left";
    ctx.textBaseline = "middle";
    for (const m of shown) {
      if (!m.chosen && labelled >= MAX_LABELS) continue;
      const size = m.chosen ? 12 : 10;
      ctx.font = (m.chosen ? "500 " : "400 ") + size + 'px "IBM Plex Mono", monospace';
      const w = ctx.measureText(m.place.city).width;
      const x = m.x + 5, y = m.y - size / 2;
      // Vurgulu yerin adı her hâlükârda yazılır; ötekiler yer bulamazsa atlanır.
      if (!m.chosen && overlaps(x, y, w, size)) continue;
      taken.push({ x, y, w, h: size });
      if (!m.chosen) labelled++;
      ctx.fillStyle = m.chosen ? C.amber : C.ink;
      ctx.globalAlpha = m.chosen ? 1 : 0.72;
      ctx.fillText(m.place.city, x, m.y);
      ctx.globalAlpha = 1;
    }
    ctx.textBaseline = "alphabetic";
    return shown.map(m => ({ x: m.x, y: m.y, place: m.place }));
  }

  function drawEndpoint(lat, lon, code) {
    const q = proj.at(lat, lon);
    if (!q.vis) return;
    ctx.beginPath(); ctx.arc(q.x, q.y, 4.5, 0, Math.PI * 2);
    ctx.strokeStyle = C.teal; ctx.lineWidth = 2; ctx.stroke();
    ctx.beginPath(); ctx.arc(q.x, q.y, 1.6, 0, Math.PI * 2);
    ctx.fillStyle = C.teal; ctx.fill();
    ctx.font = '500 11px "IBM Plex Mono", monospace';
    ctx.fillStyle = C.ink;
    ctx.textAlign = "center";
    ctx.fillText(code, q.x, q.y - 11);
  }

  /* ---------------- ana çizim ---------------- */

  /**
   * @param o.route        buildRoute() çıktısı
   * @param o.fraction     rotanın kat edilen oranı, 0–1
   * @param o.places       işaretlenecek yerler (registry.all())
   * @param o.highlighted  vurgulu kodlar (Set)
   * @param o.world        createWorld() çıktısı
   * @param o.sun          [enlem, boylam] alt-güneş noktası; yoksa gece çizilmez
   * @param o.trails       tamamlanmış rotaların [ [lat,lon], ... ] dizileri
   */
  function draw(o = {}) {
    if (!W) return;
    const { route, fraction = 0, places = [], highlighted = new Set(), world, sun, trails = [] } = o;

    const base = Math.min(W, H) * 0.40;
    proj = createProjection(view.lat, view.lon, W / 2, H / 2 - 14, base * view.zoom);

    ctx.clearRect(0, 0, W, H);
    ctx.fillStyle = C.space;
    ctx.fillRect(0, 0, W, H);
    ctx.lineJoin = "round";
    ctx.lineCap = "round";

    // küre gövdesi
    const g = ctx.createRadialGradient(
      proj.cx - proj.r * 0.35, proj.cy - proj.r * 0.4, proj.r * 0.1,
      proj.cx, proj.cy, proj.r
    );
    g.addColorStop(0, C["globe-a"]);
    g.addColorStop(1, C["globe-b"]);
    ctx.beginPath(); ctx.arc(proj.cx, proj.cy, proj.r, 0, Math.PI * 2);
    ctx.fillStyle = g; ctx.fill();

    ctx.save();
    ctx.beginPath(); ctx.arc(proj.cx, proj.cy, proj.r, 0, Math.PI * 2);
    ctx.clip();

    // kara ve göller
    if (world?.ready) {
      if (view.zoom >= FINE_DETAIL_ZOOM) world.ensureDetail();
      const land = world.land(view.zoom);
      fillLand(land, C.land);
      strokeCoast(land, C["land-line"], 0.9);
      fillLand(world.lakes(), C.lake);
    }

    // paralel ve meridyenler
    ctx.lineWidth = 1;
    ctx.strokeStyle = C.grat;
    ctx.globalAlpha = 0.75;
    for (let lat = -80; lat <= 80; lat += 20) {
      const pts = [];
      for (let lon = -180; lon <= 180; lon += 3) pts.push([lat, lon]);
      strokePolyline(pts);
    }
    for (let lon = -180; lon < 180; lon += 20) {
      const pts = [];
      for (let lat = -90; lat <= 90; lat += 3) pts.push([lat, lon]);
      strokePolyline(pts);
    }
    ctx.globalAlpha = 1;
    ctx.lineWidth = 1.4;
    ctx.strokeStyle = C.limb;
    const eq = [];
    for (let lon = -180; lon <= 180; lon += 2) eq.push([0, lon]);
    strokePolyline(eq);

    // gündüz/gece sınırı — kara ve ağın üstüne, rotanın altına
    if (sun) drawNight(sun);

    // tamamlanmış uçuşların izleri
    if (trails.length) {
      ctx.lineWidth = 1.2;
      ctx.strokeStyle = C.muted;
      ctx.globalAlpha = 0.38;
      for (const trail of trails) strokePolyline(trail);
      ctx.globalAlpha = 1;
    }

    ctx.restore();

    // dış hat
    ctx.beginPath(); ctx.arc(proj.cx, proj.cy, proj.r, 0, Math.PI * 2);
    ctx.lineWidth = 1.4; ctx.strokeStyle = C.limb; ctx.stroke();

    // yerler
    markers = places.length ? drawPlaces(places, highlighted, view.zoom) : [];

    if (route) {
      const path = samplePath(route, ROUTE_STEPS);
      const cut = Math.round(fraction * ROUTE_STEPS);

      ctx.lineWidth = 1.6;
      ctx.setLineDash([4, 5]);
      ctx.strokeStyle = C.muted;
      strokePolyline(path.slice(Math.max(0, cut - 1)));
      ctx.setLineDash([]);

      ctx.lineWidth = 2.4;
      ctx.strokeStyle = C.amber;
      ctx.shadowColor = C.amber;
      ctx.shadowBlur = 10;
      strokePolyline(path.slice(0, cut + 1));
      ctx.shadowBlur = 0;

      drawEndpoint(path[0][0], path[0][1], route.o);
      drawEndpoint(path[ROUTE_STEPS][0], path[ROUTE_STEPS][1], route.d);

      // uçak
      const t = Math.min(0.9999, fraction);
      const pv = path[Math.round(t * ROUTE_STEPS)];
      const nv = path[Math.min(ROUTE_STEPS, Math.round(t * ROUTE_STEPS) + 1)];
      const p1 = proj.at(pv[0], pv[1]), p2 = proj.at(nv[0], nv[1]);
      if (p1.vis) {
        ctx.save();
        ctx.translate(p1.x, p1.y);
        ctx.rotate(Math.atan2(p2.y - p1.y, p2.x - p1.x));
        ctx.shadowColor = C.amber;
        ctx.shadowBlur = 14;
        ctx.fillStyle = C.amber;
        ctx.beginPath();
        ctx.moveTo(11, 0); ctx.lineTo(-5, 7); ctx.lineTo(-1.5, 0); ctx.lineTo(-5, -7);
        ctx.closePath();
        ctx.fill();
        ctx.restore();
      }
    }
  }

  return {
    view, aim, moveTo, rotateBy, rotateByPixels, stopMomentum, releaseMomentum,
    zoomBy, setZoom, zoomAnchored, resetView, ease,
    readTokens, resize, placeAt, draw,
    locate: (x, y) => proj?.locate(x, y) ?? null,
    get projection() { return proj; }
  };
}
