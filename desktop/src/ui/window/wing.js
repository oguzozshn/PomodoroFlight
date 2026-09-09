/**
 * Pencereden görünen kanat.
 *
 * Kök gövdenin yanında, çerçevenin dışında kalır; uç uzakta olduğu için
 * perspektifte yukarı çıkar ve incelir. Üst yüzey gökyüzünün rengini alır,
 * arka kenar gölgede kalır — bu yüzden gün batımında kanat da kızarır.
 *
 * Birkaç model var; hangisinin çizileceği ayarlardan seçilir.
 */

/** Kuadratik Bézier üzerinde t oranındaki nokta. */
function qAt(p0, c, p1, t) {
  const u = 1 - t;
  return [
    u * u * p0[0] + 2 * u * t * c[0] + t * t * p1[0],
    u * u * p0[1] + 2 * u * t * c[1] + t * t * p1[1]
  ];
}

const rgba = (c, a) => "rgba(" + c[0] + "," + c[1] + "," + c[2] + "," + a + ")";

/** Ortak gövde: ana yüzey, ön kenar, panel çizgileri, flap kaportaları. */
function planform(ctx, geo, paint, options = {}) {
  const { rootLead, tipLead, tipTrail, rootTrail, leadCtrl, trailCtrl } = geo;
  const { lit, shade } = paint;
  const { panels = [0.24, 0.46, 0.68, 0.86], flaps = [0.40, 0.66], w, h } = options;

  ctx.beginPath();
  ctx.moveTo(...rootLead);
  ctx.quadraticCurveTo(leadCtrl[0], leadCtrl[1], ...tipLead);
  ctx.lineTo(...tipTrail);
  ctx.quadraticCurveTo(trailCtrl[0], trailCtrl[1], ...rootTrail);
  ctx.closePath();

  const surface = ctx.createLinearGradient(rootLead[0], tipLead[1], tipLead[0], rootTrail[1]);
  surface.addColorStop(0, rgba(shade, 0.90));
  surface.addColorStop(0.5, rgba(lit, 0.90));
  surface.addColorStop(1, rgba(lit, 0.96));
  ctx.fillStyle = surface;
  ctx.fill();

  // ön kenar: gökyüzüne bakan ince parlak şerit
  ctx.beginPath();
  ctx.moveTo(...rootLead);
  ctx.quadraticCurveTo(leadCtrl[0], leadCtrl[1], ...tipLead);
  ctx.strokeStyle = rgba(lit, 0.98);
  ctx.lineWidth = Math.max(1.4, h * 0.005);
  ctx.stroke();

  // panel çizgileri: iki kenar eğrisi arasında
  ctx.strokeStyle = rgba(shade, 0.32);
  ctx.lineWidth = 1;
  for (const t of panels) {
    const a = qAt(tipLead, leadCtrl, rootLead, t);
    const b = qAt(tipTrail, trailCtrl, rootTrail, t);
    ctx.beginPath();
    ctx.moveTo(a[0], a[1]);
    ctx.lineTo(b[0], b[1]);
    ctx.stroke();
  }

  // flap kızak kaportaları: arka kenara oturur, altından sarkar
  for (const t of flaps) {
    const base = qAt(tipTrail, trailCtrl, rootTrail, t);
    const next = qAt(tipTrail, trailCtrl, rootTrail, t + 0.02);
    const ang = Math.atan2(next[1] - base[1], next[0] - base[0]);
    const len = w * 0.12, drop = h * 0.028;
    ctx.save();
    ctx.translate(base[0], base[1]);
    ctx.rotate(ang);
    ctx.beginPath();
    ctx.moveTo(-len * 0.35, 0);
    ctx.quadraticCurveTo(len * 0.25, drop, len * 0.62, drop * 0.30);
    ctx.lineTo(len * 0.30, -2);
    ctx.closePath();
    ctx.fillStyle = rgba(shade, 0.92);
    ctx.fill();
    ctx.restore();
  }

  // arka kenardaki ince gölge: kanat düz levha gibi durmasın
  ctx.beginPath();
  ctx.moveTo(...tipTrail);
  ctx.quadraticCurveTo(trailCtrl[0], trailCtrl[1], ...rootTrail);
  ctx.strokeStyle = "rgba(52,62,76,.38)";
  ctx.lineWidth = 1.2;
  ctx.stroke();
}

/** A · Dik winglet: uçtan yukarı kalkan ayrı kanatçık (737 MAX, A320neo). */
function modernWinglet(ctx, rect, paint) {
  const { x, y, w, h } = rect;
  const px = (u, v) => [x + w * u, y + h * v];
  const geo = {
    rootLead: px(1.24, 0.815), tipLead: px(0.11, 0.712),
    tipTrail: px(0.165, 0.775), rootTrail: px(1.24, 1.06),
    leadCtrl: px(0.64, 0.735), trailCtrl: px(0.60, 0.885)
  };
  planform(ctx, geo, paint, { w, h });

  ctx.beginPath();
  ctx.moveTo(...geo.tipLead);
  ctx.quadraticCurveTo(x + w * 0.055, y + h * 0.635, x + w * 0.022, y + h * 0.545);
  ctx.lineTo(x + w * 0.082, y + h * 0.565);
  ctx.quadraticCurveTo(x + w * 0.125, y + h * 0.665, ...geo.tipTrail);
  ctx.closePath();
  const fin = ctx.createLinearGradient(x, y + h * 0.53, x + w * 0.17, y + h * 0.78);
  fin.addColorStop(0, rgba(paint.lit, 0.93));
  fin.addColorStop(1, rgba(paint.shade, 0.93));
  ctx.fillStyle = fin;
  ctx.fill();
}

/** B · Kıvrık uç: yüzeyle birleşerek yukarı kıvrılan sharklet (737-800). */
function blendedTip(ctx, rect, paint) {
  const { x, y, w, h } = rect;
  const px = (u, v) => [x + w * u, y + h * v];
  const geo = {
    rootLead: px(1.24, 0.815), tipLead: px(0.16, 0.700),
    tipTrail: px(0.215, 0.772), rootTrail: px(1.24, 1.06),
    leadCtrl: px(0.66, 0.732), trailCtrl: px(0.62, 0.884)
  };
  planform(ctx, geo, paint, { w, h, flaps: [0.42, 0.68] });

  // Uç, ayrı bir kanatçık değil; yüzeyin devamı olarak kıvrılır.
  ctx.beginPath();
  ctx.moveTo(...geo.tipLead);
  ctx.bezierCurveTo(
    x + w * 0.075, y + h * 0.672,
    x + w * 0.030, y + h * 0.612,
    x + w * 0.028, y + h * 0.528
  );
  ctx.lineTo(x + w * 0.086, y + h * 0.552);
  ctx.bezierCurveTo(
    x + w * 0.098, y + h * 0.630,
    x + w * 0.150, y + h * 0.712,
    ...geo.tipTrail
  );
  ctx.closePath();
  const fin = ctx.createLinearGradient(x + w * 0.02, y + h * 0.52, x + w * 0.20, y + h * 0.78);
  fin.addColorStop(0, rgba(paint.lit, 0.95));
  fin.addColorStop(0.6, rgba(paint.lit, 0.90));
  fin.addColorStop(1, rgba(paint.shade, 0.92));
  ctx.fillStyle = fin;
  ctx.fill();
}

/** C · Düz uç: kanatçıksız klasik kanat (A340, eski 737). */
function plainTip(ctx, rect, paint) {
  const { x, y, w, h } = rect;
  const px = (u, v) => [x + w * u, y + h * v];
  const geo = {
    rootLead: px(1.24, 0.828), tipLead: px(0.05, 0.735),
    tipTrail: px(0.105, 0.788), rootTrail: px(1.24, 1.06),
    leadCtrl: px(0.62, 0.748), trailCtrl: px(0.58, 0.892)
  };
  planform(ctx, geo, paint, { w, h, panels: [0.20, 0.42, 0.64, 0.84] });

  // uçta ince bir kenar aydınlığı
  ctx.beginPath();
  ctx.moveTo(...geo.tipLead);
  ctx.lineTo(...geo.tipTrail);
  ctx.strokeStyle = rgba(paint.lit, 0.9);
  ctx.lineWidth = 1.6;
  ctx.stroke();
}

/** D · Uzak kanat: köşede duran, çerçeveyi az kaplayan görünüm. */
function distantWing(ctx, rect, paint) {
  const { x, y, w, h } = rect;
  const px = (u, v) => [x + w * u, y + h * v];
  const geo = {
    rootLead: px(1.24, 0.905), tipLead: px(0.34, 0.830),
    tipTrail: px(0.375, 0.872), rootTrail: px(1.24, 1.06),
    leadCtrl: px(0.78, 0.855), trailCtrl: px(0.74, 0.955)
  };
  planform(ctx, geo, paint, { w, h, panels: [0.30, 0.56, 0.80], flaps: [0.46, 0.72] });

  ctx.beginPath();
  ctx.moveTo(...geo.tipLead);
  ctx.quadraticCurveTo(x + w * 0.300, y + h * 0.788, x + w * 0.286, y + h * 0.735);
  ctx.lineTo(x + w * 0.324, y + h * 0.748);
  ctx.quadraticCurveTo(x + w * 0.352, y + h * 0.808, ...geo.tipTrail);
  ctx.closePath();
  ctx.fillStyle = rgba(paint.lit, 0.92);
  ctx.fill();
}

export const WING_MODELS = {
  winglet: { title: "Dik winglet", draw: modernWinglet },
  blended: { title: "Kıvrık uç", draw: blendedTip },
  plain: { title: "Düz uç", draw: plainTip },
  distant: { title: "Uzak kanat", draw: distantWing }
};

export const DEFAULT_WING = "distant";

/**
 * Seçili modeli çizer; palet yoksa nötr gri kullanılır.
 * Sağ koltukta kanat pencerenin öbür yanından girer, o yüzden aynalanır.
 */
export function drawWing(ctx, rect, palette, model = DEFAULT_WING, seat = "left") {
  const entry = WING_MODELS[model] ?? WING_MODELS[DEFAULT_WING];
  const paint = {
    lit: palette ? palette.cloudLit : [236, 240, 246],
    shade: palette ? palette.cloudShade : [176, 186, 199]
  };
  ctx.save();
  if (seat === "right") {
    // Pencere deliğinin dikey ekseninde aynala.
    ctx.translate(rect.x * 2 + rect.w, 0);
    ctx.scale(-1, 1);
  }
  entry.draw(ctx, rect, paint);
  ctx.restore();
}
