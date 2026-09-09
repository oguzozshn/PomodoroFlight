/**
 * Gerçek zamanlı pencere: rotanın o anki noktasından, gerçek saate göre
 * aydınlanan bir gökyüzü. Android sürümündeki ui/window/RealtimeWindow.kt.
 *
 * Katmanlar sırayla: gökyüzü geçişi, güneşin ufka vuran ışığı, yıldızlar,
 * güneş kursu, deniz, kara, şehir ışıkları, hava perspektifi, bulut tabakası.
 *
 * Rastgele alanlar (yıldız, bulut) tohumlu bir üreteçten geliyor; Kotlin'in
 * `Random` sınıfıyla birebir aynı sayılar değil ama dağılım aynı, sahne her
 * açılışta aynı görünüyor.
 */

import { RAD, DEG, vec } from "../../core/geo.js";
import { sunElevation, subsolarPoint } from "../../core/sun.js";
import { createSkyCamera, EARTH_RADIUS_KM } from "./sky-camera.js";
import { paletteFor, css } from "./sky-palette.js";
import { horizonPolyline, traceGround, traceLand } from "./sky-geometry.js";
import { qualityOf, offsetForSeat, DEFAULT_QUALITY, CLOUD_COUNT, STAR_COUNT } from "./quality.js";

/** Kameranın aşağı eğimi. */
const VIEW_PITCH_DEG = -13;
/** Dikey görüş açısı. */
const VIEW_FOV_DEG = 62;

/** Seyir hızı (km/sn) — bulutların geçiş hızını belirler. */
const GROUND_SPEED_KM_S = 900 / 3600;

const CLOUD_ALT_KM = 2.6;
const CLOUD_SPAN_U = 420;
const CLOUD_SPAN_V = 320;
const PUFFS_PER_CLOUD = 5;

/** Uçuş hiç başlamadıysa bile pencere havada görünsün. */
const MIN_ALTITUDE_M = 1400;

/** Tohumlu üreteç: sahne her açılışta aynı olsun. */
function mulberry32(seed) {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
const range = (rnd, lo, hi) => lo + rnd() * (hi - lo);

/** Sabit yıldız alanı: bir kez üretilir, dünyayla birlikte döner. */
function createStarField(count) {
  const rnd = mulberry32(20240815);
  const xyz = new Float64Array(count * 3);
  const magnitude = new Float32Array(count);
  for (let i = 0; i < count; i++) {
    // Küre üzerinde düzgün dağılım.
    const z = range(rnd, -1, 1);
    const a = range(rnd, 0, Math.PI * 2);
    const r = Math.sqrt(1 - z * z);
    xyz[i * 3] = r * Math.cos(a);
    xyz[i * 3 + 1] = r * Math.sin(a);
    xyz[i * 3 + 2] = z;
    const m = rnd();
    magnitude[i] = m * m;
  }

  return function draw(ctx, camera, night) {
    const minUp = -Math.sin(camera.horizonDip);
    for (let i = 0; i < count; i++) {
      const x = xyz[i * 3], y = xyz[i * 3 + 1], z = xyz[i * 3 + 2];
      if (x * camera.upX + y * camera.upY + z * camera.upZ < minUp) continue;
      const depth = camera.depth(x, y, z);
      if (depth <= 1e-6) continue;
      const sx = camera.screenX(x, y, z, depth);
      const sy = camera.screenY(x, y, z, depth);
      if (sx < 0 || sx > camera.width || sy < 0 || sy > camera.height) continue;
      ctx.globalAlpha = night * (0.25 + 0.75 * magnitude[i]);
      ctx.beginPath();
      ctx.arc(sx, sy, camera.height * (0.0009 + 0.0013 * magnitude[i]), 0, Math.PI * 2);
      ctx.fillStyle = "#fff";
      ctx.fill();
    }
    ctx.globalAlpha = 1;
  };
}

/**
 * Uçağın altındaki bulut tabakası.
 *
 * Her puf, kameranın yatay düzleminde (sağ, ileri) km cinsinden durur; uçak
 * ilerledikçe bu düzlem kayar, uzaktakiler yavaş, yakındakiler hızlı geçer —
 * paralaks kendiliğinden doğar.
 */
function createCloudField(count) {
  const rnd = mulberry32(770315);
  const n = count * PUFFS_PER_CLOUD;
  const u = new Float64Array(n), v = new Float64Array(n);
  const radius = new Float64Array(n), lift = new Float64Array(n);

  let i = 0;
  for (let cloud = 0; cloud < count; cloud++) {
    const cu = range(rnd, 0, CLOUD_SPAN_U);
    const cv = range(rnd, 0, CLOUD_SPAN_V);
    const scale = range(rnd, 0.55, 1.9);
    const cz = range(rnd, -0.7, 1.1);
    for (let puff = 0; puff < PUFFS_PER_CLOUD; puff++) {
      u[i] = cu + range(rnd, -2.6, 2.6) * scale;
      v[i] = cv + range(rnd, -2, 2) * scale;
      radius[i] = range(rnd, 0.55, 1.7) * scale;
      lift[i] = cz + range(rnd, -0.15, 0.35) * scale;
      i++;
    }
  }

  return function draw(ctx, camera, palette, time, offsetDeg) {
    // Uçağın bakış çerçevesindeki kayması: burun yönü ile kamera açısı farkı.
    const relative = offsetDeg * RAD;
    const driftU = -GROUND_SPEED_KM_S * Math.sin(-relative);
    const driftV = -GROUND_SPEED_KM_S * Math.cos(-relative);

    for (let k = 0; k < n; k++) {
      let pu = (u[k] + driftU * time) % CLOUD_SPAN_U;
      if (pu < 0) pu += CLOUD_SPAN_U;
      pu -= CLOUD_SPAN_U / 2;
      let pv = (v[k] + driftV * time) % CLOUD_SPAN_V;
      if (pv < 0) pv += CLOUD_SPAN_V;
      pv += 3;

      const distance = Math.hypot(pu, pv);
      if (distance < 2 || distance > camera.horizonKm) continue;

      // Dünyanın eğriliği: uzaktaki bulut alçalır.
      const drop = (distance * distance) / (2 * EARTH_RADIUS_KM);
      const dz = CLOUD_ALT_KM + lift[k] - camera.altitudeKm - drop;
      const elevation = Math.atan2(dz, distance);
      const bear = camera.azimuthRad + Math.atan2(pu, pv);
      const dir = camera.direction(elevation, bear);

      const depth = camera.depth(dir[0], dir[1], dir[2]);
      if (depth <= 1e-6) continue;
      const x = camera.screenX(dir[0], dir[1], dir[2], depth);
      const y = camera.screenY(dir[0], dir[1], dir[2], depth);

      const slant = Math.hypot(distance, dz);
      const screenR = (camera.focal * radius[k]) / slant;
      if (screenR < 0.7) continue;
      if (x < -screenR * 2 || x > camera.width + screenR * 2) continue;
      if (y < -screenR * 2 || y > camera.height + screenR * 2) continue;

      // Uzaklaştıkça puslanıp ufka karışırlar.
      const fade = Math.max(0, Math.min(1, 1 - distance / camera.horizonKm));
      const alpha = (0.16 + 0.42 * fade) * (screenR / (screenR + 3));

      ctx.beginPath();
      ctx.arc(x, y + screenR * 0.16, screenR, 0, Math.PI * 2);
      ctx.fillStyle = css(palette.cloudShade, alpha * 0.75);
      ctx.fill();

      ctx.beginPath();
      ctx.arc(x, y - screenR * 0.22, screenR * 0.78, 0, Math.PI * 2);
      ctx.fillStyle = css(palette.cloudLit, alpha);
      ctx.fill();
    }
  };
}

/** Sonsuzdaki bir yönün (güneş, yıldız) ekrandaki yeri. */
function projectDirection(camera, dir) {
  const depth = camera.depth(dir[0], dir[1], dir[2]);
  if (depth <= 1e-6) return null;
  return {
    x: camera.screenX(dir[0], dir[1], dir[2], depth),
    y: camera.screenY(dir[0], dir[1], dir[2], depth)
  };
}

/**
 * @param quality quality.js'teki anahtar; nesne sayılarını ve ufuk çözünürlüğünü
 *                belirler. Ayar değişince yeni bir örnek kurulur.
 */
export function createRealtimeWindow(quality = DEFAULT_QUALITY) {
  const q = qualityOf(quality);
  // Alanlar kaliteden bağımsız: sahne her ayarda aynı, yalnızca daha ince çizilir.
  const drawStars = createStarField(STAR_COUNT);
  const drawClouds = createCloudField(CLOUD_COUNT);

  /** Gece şehirler küçük sıcak lekeler hâlinde görünür. */
  function drawCityLights(ctx, camera, places, night) {
    let drawn = 0;
    for (const place of places) {
      if (drawn > 220) break;
      const p = vec(place.lat, place.lon);
      if (p[0] * camera.upX + p[1] * camera.upY + p[2] * camera.upZ < camera.horizonCos) continue;
      const vx = p[0] - camera.posX, vy = p[1] - camera.posY, vz = p[2] - camera.posZ;
      const depth = camera.depth(vx, vy, vz);
      if (depth <= 1e-6) continue;
      const x = camera.screenX(vx, vy, vz, depth);
      const y = camera.screenY(vx, vy, vz, depth);
      if (x < 0 || x > camera.width || y < 0 || y > camera.height) continue;
      drawn++;
      const r = camera.height * (place.rank === 0 ? 0.006 : 0.004);
      const g = ctx.createRadialGradient(x, y, 0, x, y, r * 3);
      g.addColorStop(0, "rgba(255,214,150," + (0.75 * night).toFixed(3) + ")");
      g.addColorStop(1, "rgba(255,190,120,0)");
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(x, y, r * 3, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  /**
   * @param o.route      buildRoute() çıktısı
   * @param o.fraction   rotanın kat edilen oranı
   * @param o.altitudeM  gösterge irtifası
   * @param o.world      createWorld() çıktısı
   * @param o.places     şehir ışıkları için yerler
   * @param o.time       saniye cinsinden akan zaman (bulut kayması)
   */
  function draw(ctx, width, height, o) {
    const {
      route, fraction = 0, altitudeM = 0, world, places = [],
      time = 0, now = Date.now(), seat = "left"
    } = o;
    if (!route || width < 2 || height < 2) return null;

    const [lat, lon] = route.center && fraction === undefined
      ? route.center
      : (() => {
          const t = Math.max(0, Math.min(0.9999, fraction));
          const a = route.a, b = route.b;
          const d = Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
          const w = Math.acos(d);
          if (w < 1e-8) return [Math.asin(a[2]) * DEG, Math.atan2(a[1], a[0]) * DEG];
          const s = Math.sin(w), k1 = Math.sin((1 - t) * w) / s, k2 = Math.sin(t * w) / s;
          const x = a[0] * k1 + b[0] * k2, y = a[1] * k1 + b[1] * k2, z = a[2] * k1 + b[2] * k2;
          return [Math.asin(Math.max(-1, Math.min(1, z))) * DEG, Math.atan2(y, x) * DEG];
        })();

    // Uçağın burnu varışa dönük; pencere burnun solunda.
    const [nLat, nLon] = (() => {
      const t = Math.min(1, Math.max(0, fraction) + 0.004);
      const a = route.a, b = route.b;
      const d = Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
      const w = Math.acos(d);
      if (w < 1e-8) return [lat, lon];
      const s = Math.sin(w), k1 = Math.sin((1 - t) * w) / s, k2 = Math.sin(t * w) / s;
      const x = a[0] * k1 + b[0] * k2, y = a[1] * k1 + b[1] * k2, z = a[2] * k1 + b[2] * k2;
      return [Math.asin(Math.max(-1, Math.min(1, z))) * DEG, Math.atan2(y, x) * DEG];
    })();

    const p1 = lat * RAD, p2 = nLat * RAD, dLon = (nLon - lon) * RAD;
    const heading = (Math.atan2(
      Math.sin(dLon) * Math.cos(p2),
      Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dLon)
    ) * DEG + 360) % 360;

    const camera = createSkyCamera({
      lat, lon,
      altitudeKm: Math.max(MIN_ALTITUDE_M, altitudeM) / 1000,
      azimuthDeg: heading + offsetForSeat(seat),
      pitchDeg: VIEW_PITCH_DEG,
      fovDeg: VIEW_FOV_DEG,
      width, height
    });

    const elevation = sunElevation(lat, lon, now);
    const palette = paletteFor(elevation);
    const sub = subsolarPoint(now);
    const sunDir = vec(sub[0], sub[1]);

    const horizon = horizonPolyline(camera, q.horizonSteps);
    // Ufuk yüksekliği görüşün ortasından alınır: eğri olduğu için kenardaki
    // değer birkaç yüz piksel farklı çıkıyor ve gökyüzü geçişi kayıyordu.
    const horizonY = horizon.length >= 2
      ? horizon[((horizon.length / 2) | 0) | 1]
      : height / 2;

    // 1) gökyüzü: başucundan ufka inen geçiş
    const sky = ctx.createLinearGradient(0, -height * 0.25, 0, horizonY);
    sky.addColorStop(0, css(palette.zenith));
    sky.addColorStop(1, css(palette.horizon));
    ctx.fillStyle = sky;
    ctx.fillRect(0, 0, width, height);

    // 2) güneşin bulunduğu yönde ufka vuran sıcak ışık
    const sunPoint = projectDirection(camera, sunDir);
    if (sunPoint && palette.glowStrength > 0.02) {
      const glowY = Math.max(horizonY - height, Math.min(horizonY + height * 0.15, sunPoint.y));
      const r = height * 0.9;
      const g = ctx.createRadialGradient(sunPoint.x, glowY, 0, sunPoint.x, glowY, r);
      g.addColorStop(0, css(palette.glow, 0.55 * palette.glowStrength));
      g.addColorStop(0.5, css(palette.glow, 0.16 * palette.glowStrength));
      g.addColorStop(1, css(palette.glow, 0));
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(sunPoint.x, glowY, r, 0, Math.PI * 2);
      ctx.fill();
    }

    // 3) yıldızlar
    if (palette.night > 0.04) drawStars(ctx, camera, palette.night);

    // 4) güneş kursu
    if (sunPoint && elevation > -1) {
      const r = height * 0.028 * 2.6;
      const g = ctx.createRadialGradient(sunPoint.x, sunPoint.y, 0, sunPoint.x, sunPoint.y, r);
      g.addColorStop(0, "rgba(255,255,255,1)");
      g.addColorStop(0.38, css(palette.glow, 0.9));
      g.addColorStop(1, css(palette.glow, 0));
      ctx.fillStyle = g;
      ctx.beginPath();
      ctx.arc(sunPoint.x, sunPoint.y, r, 0, Math.PI * 2);
      ctx.fill();
    }

    // 5) yer yüzeyi
    if (traceGround(ctx, camera, horizon)) {
      ctx.fillStyle = css(palette.sea);
      ctx.fill();

      // 6) kara ve şehir ışıkları yalnızca yerin üzerine düşebilir
      ctx.save();
      traceGround(ctx, camera, horizon);
      ctx.clip();
      // Pencereden bakışta ufuk birkaç yüz kilometre ötede; kaba katmanın
      // 5.143 noktası bu mesafede kıyıyı düz bir çizgi yapıyor. İnce katman
      // istenir, gelene kadar kaba olanla çizilir.
      const land = world?.ready ? world.land(3) : null;
      if (land && traceLand(ctx, camera, land)) {
        ctx.fillStyle = css(palette.land);
        ctx.fill("nonzero");
      }
      if (palette.night > 0.25) drawCityLights(ctx, camera, places, palette.night);
      ctx.restore();
    }

    // 7) hava perspektifi: uzaklık arttıkça yer gökyüzünün rengine doğru silinir
    const hazeTop = horizonY - height * 0.015;
    const hazeBottom = height * 1.02;
    if (hazeBottom > hazeTop) {
      const h = ctx.createLinearGradient(0, hazeTop, 0, hazeBottom);
      h.addColorStop(0, css(palette.horizon, 0.97));
      h.addColorStop(0.10, css(palette.horizon, 0.74));
      h.addColorStop(0.28, css(palette.horizon, 0.42));
      h.addColorStop(0.58, css(palette.horizon, 0.20));
      h.addColorStop(1, css(palette.horizon, 0.08));
      ctx.fillStyle = h;
      ctx.fillRect(0, hazeTop, width, hazeBottom - hazeTop);
    }

    // 8) bulut tabakası
    drawClouds(ctx, camera, palette, time, offsetForSeat(seat));

    return { camera, palette, elevation, lat, lon, heading };
  }

  return { draw };
}
