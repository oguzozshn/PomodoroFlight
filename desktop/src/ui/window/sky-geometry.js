/**
 * Pencereden görünen yer yüzeyinin geometrisi: ufuk eğrisi, zemin ve kara
 * parçaları. Android sürümündeki ui/window/SkyGeometry.kt karşılığı.
 */

import { RAD } from "../../core/geo.js";

/** Ufuk eğrisinin kaç parçaya bölünerek çizileceği. */
const HORIZON_STEPS = 96;

/** Ufuk, bakış yönünün bu kadar sağına ve soluna kadar örneklenir (derece). */
const HORIZON_SPREAD = 80;

/** Kameranın hemen önündeki kırpma düzlemi (dünya yarıçapı biriminde). */
const NEAR = 1e-7;

/** Görüş piramidi kenarlardan biraz geniş tutulur; kırpık çerçeveye değmesin. */
const FRUSTUM_MARGIN = 1.06;

/** Ufuk çemberinde iki nokta arası bu açıyı aşarsa araya yay noktaları konur. */
const CAP_ARC_STEP_RAD = 0.05;

/** Ufka bastırılmış iki komşu nokta bundan yakınsa ikincisi atlanır. */
const CAP_MERGE_RAD = 0.008;

/** Tek bir yayın en fazla kaç parçaya bölüneceği. */
const CAP_ARC_LIMIT = 160;

/**
 * Ufuk çizgisinin ekran noktaları: `[x0, y0, x1, y1, ...]`.
 *
 * Nokta nokta hesaplandığı için dünyanın eğriliği kendiliğinden çıkar; yüksek
 * irtifada ufkun hafif kavisi de, alçaktayken düzleşmesi de doğru olur.
 */
export function horizonPolyline(camera, steps = HORIZON_STEPS) {
  const angle = camera.horizonDip;
  const out = [];
  for (let i = 0; i <= steps; i++) {
    const offset = (-HORIZON_SPREAD + (2 * HORIZON_SPREAD * i) / steps) * RAD;
    const p = camera.surfaceAt(angle, camera.azimuthRad + offset);
    const vx = p[0] - camera.posX, vy = p[1] - camera.posY, vz = p[2] - camera.posZ;
    const depth = camera.depth(vx, vy, vz);
    if (depth <= NEAR) continue;
    out.push(camera.screenX(vx, vy, vz, depth), camera.screenY(vx, vy, vz, depth));
  }
  return out;
}

/** Ufkun altında kalan, yani yer yüzeyinin kapladığı alan. */
export function traceGround(ctx, camera, horizon) {
  if (horizon.length < 4) return false;
  const left = -camera.width, right = camera.width * 2, bottom = camera.height * 2;
  ctx.beginPath();
  ctx.moveTo(left, horizon[1]);
  for (let i = 0; i < horizon.length; i += 2) ctx.lineTo(horizon[i], horizon[i + 1]);
  ctx.lineTo(right, horizon[horizon.length - 1]);
  ctx.lineTo(right, bottom);
  ctx.lineTo(left, bottom);
  ctx.closePath();
  return true;
}

/**
 * Halkayı ufuk çemberine bastırır ve aynı geçişte seyreltir.
 *
 * Ufkun ötesindeki nokta atılmaz, bulunduğu yönde çembere taşınır: halka kapalı
 * ve aynı yönde dönen tek bir eğri olarak kalır, bu yüzden körfezler kara
 * sanılmaz. Kırpmayla yapılan çözümde halkanın iki ucunu birleştiren kiriş
 * Ege'yi baştan başa kara yapıyordu.
 *
 * Arkada kalan kıyıların binlerce noktası çemberin aynı yerine düştüğü için
 * birbirine çok yakın düşenler atlanır; arada büyük bir sıçrama varsa yay
 * üzerine ara nokta konur. Böylece bir kıtanın on bin noktası birkaç yüze iner.
 */
function clampToHorizon(src, count, out, ux, uy, uz, cap) {
  const ring = Math.sqrt(Math.max(0, 1 - cap * cap));
  const capacity = Math.floor(out.length / 3);
  let written = 0;
  let hasPrevious = false, px = 0, py = 0, pz = 0;

  for (let i = 0; i < count; i++) {
    const x = src[i * 3], y = src[i * 3 + 1], z = src[i * 3 + 2];
    const height = x * ux + y * uy + z * uz;

    if (height >= cap) {
      if (written >= capacity) return -1;
      out[written * 3] = x; out[written * 3 + 1] = y; out[written * 3 + 2] = z;
      written++;
      hasPrevious = false;
      continue;
    }

    // Noktanın ufuk düzlemindeki yönü; büyüklüğü bir yapılıp çembere taşınır.
    let wx = x - height * ux, wy = y - height * uy, wz = z - height * uz;
    const length = Math.hypot(wx, wy, wz);
    if (length < 1e-12) continue;
    wx /= length; wy /= length; wz /= length;

    if (hasPrevious) {
      const dot = Math.max(-1, Math.min(1, px * wx + py * wy + pz * wz));
      const angle = Math.acos(dot);
      if (angle < CAP_MERGE_RAD) continue;
      if (angle > CAP_ARC_STEP_RAD) {
        // Yayın ikinci ekseni: önceki noktaya dik, bu noktaya bakan yön
        let ax = wx - dot * px, ay = wy - dot * py, az = wz - dot * pz;
        const alen = Math.hypot(ax, ay, az);
        if (alen > 1e-12) {
          ax /= alen; ay /= alen; az /= alen;
          const steps = Math.min(CAP_ARC_LIMIT, Math.floor(angle / CAP_ARC_STEP_RAD));
          for (let k = 1; k < steps; k++) {
            const t = (angle * k) / steps;
            const c = Math.cos(t), sn = Math.sin(t);
            if (written >= capacity) return -1;
            out[written * 3] = cap * ux + ring * (px * c + ax * sn);
            out[written * 3 + 1] = cap * uy + ring * (py * c + ay * sn);
            out[written * 3 + 2] = cap * uz + ring * (pz * c + az * sn);
            written++;
          }
        }
      }
    }

    if (written >= capacity) return -1;
    out[written * 3] = cap * ux + ring * wx;
    out[written * 3 + 1] = cap * uy + ring * wy;
    out[written * 3 + 2] = cap * uz + ring * wz;
    written++;
    hasPrevious = true;
    px = wx; py = wy; pz = wz;
  }
  return written;
}

/**
 * Sutherland–Hodgman: kapalı bir halkayı tek bir yarı uzaya kırpar.
 * `inside` pozitif dönerse nokta içerdedir; sınır üzerinde doğrusal ara değer
 * yapılır. Çıktı tamponu yetmezse -1 döner: yarım kalmış bir halka çizilmez.
 */
function clipToHalfSpace(src, count, out, inside) {
  const capacity = Math.floor(out.length / 3);
  let written = 0;
  const last = count - 1;
  let prevX = src[last * 3], prevY = src[last * 3 + 1], prevZ = src[last * 3 + 2];
  let prevValue = inside(prevX, prevY, prevZ);

  for (let i = 0; i < count; i++) {
    const x = src[i * 3], y = src[i * 3 + 1], z = src[i * 3 + 2];
    const value = inside(x, y, z);

    if (value >= 0) {
      if (prevValue < 0) {
        if (written >= capacity) return -1;
        const t = prevValue / (prevValue - value);
        out[written * 3] = prevX + (x - prevX) * t;
        out[written * 3 + 1] = prevY + (y - prevY) * t;
        out[written * 3 + 2] = prevZ + (z - prevZ) * t;
        written++;
      }
      if (written >= capacity) return -1;
      out[written * 3] = x; out[written * 3 + 1] = y; out[written * 3 + 2] = z;
      written++;
    } else if (prevValue >= 0) {
      if (written >= capacity) return -1;
      const t = prevValue / (prevValue - value);
      out[written * 3] = prevX + (x - prevX) * t;
      out[written * 3 + 1] = prevY + (y - prevY) * t;
      out[written * 3 + 2] = prevZ + (z - prevZ) * t;
      written++;
    }

    prevX = x; prevY = y; prevZ = z;
    prevValue = value;
  }
  return written;
}

/**
 * Ufka kadar görünen kara parçalarını yola çizer.
 *
 * Her halkanın ufuk ötesinde kalan kısmı önce ufuk çizgisine bastırılır, sonra
 * halka görüş piramidinin beş düzlemine kırpılır; kalan köşeler ekrana düşürülür.
 */
export function traceLand(ctx, camera, shapes) {
  const ux = camera.upX, uy = camera.upY, uz = camera.upZ;
  const cosCap = camera.horizonCos;

  // Görüş piramidinin dört yanı. Düzlemlerin hepsi kameradan geçtiği için
  // kırpılan noktalar da ekran çerçevesinin üzerine düşer; böylece ufkun
  // arkasına dolanan yay milyonlarca piksellik koordinat üretmez.
  const tanH = camera.tanHalfHorizontal * FRUSTUM_MARGIN;
  const tanV = camera.tanHalfVertical * FRUSTUM_MARGIN;
  const rel = (x, y, z) => [x - camera.posX, y - camera.posY, z - camera.posZ];

  const planes = [
    (x, y, z) => { const v = rel(x, y, z); return camera.depth(...v) - NEAR; },
    (x, y, z) => { const v = rel(x, y, z); return tanH * camera.depth(...v) - camera.lateral(...v); },
    (x, y, z) => { const v = rel(x, y, z); return tanH * camera.depth(...v) + camera.lateral(...v); },
    (x, y, z) => { const v = rel(x, y, z); return tanV * camera.depth(...v) - camera.vertical(...v); },
    (x, y, z) => { const v = rel(x, y, z); return tanV * camera.depth(...v) + camera.vertical(...v); }
  ];

  let buffer = new Float64Array(0), scratch = new Float64Array(0);
  let drew = false;
  ctx.beginPath();

  for (const shape of shapes) {
    if (!shape.withinCap(ux, uy, uz, cosCap)) continue;
    const n = shape.pointCount;
    if (n < 3) continue;

    const need = n * 3 + 16384;
    if (buffer.length < need) {
      buffer = new Float64Array(need);
      scratch = new Float64Array(need);
    }

    // Ufkun ötesi atılmaz, ufka bastırılır; arkada kalan uzun kıyılar da
    // aynı anda seyreltilir, yoksa bir kıtanın on bin noktası boşuna gezilir.
    let count = clampToHorizon(shape.xyz, n, buffer, ux, uy, uz, cosCap);
    if (count < 3) continue;

    let failed = false;
    for (const plane of planes) {
      count = clipToHalfSpace(buffer, count, scratch, plane);
      if (count < 3) { failed = true; break; }
      buffer.set(scratch.subarray(0, count * 3));
    }
    if (failed) continue;

    let started = false;
    for (let i = 0; i < count; i++) {
      const x = scratch[i * 3], y = scratch[i * 3 + 1], z = scratch[i * 3 + 2];
      const vx = x - camera.posX, vy = y - camera.posY, vz = z - camera.posZ;
      const depth = camera.depth(vx, vy, vz);
      if (depth <= NEAR) continue;
      const sx = camera.screenX(vx, vy, vz, depth);
      const sy = camera.screenY(vx, vy, vz, depth);
      if (started) ctx.lineTo(sx, sy);
      else { ctx.moveTo(sx, sy); started = true; }
    }
    if (started) { ctx.closePath(); drew = true; }
  }
  return drew;
}
