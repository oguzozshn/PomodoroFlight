/**
 * Güneş yüksekliğine göre gökyüzü, deniz, kara ve bulut renkleri.
 *
 * Renkler elle seçilmiş birkaç anahtar kareden doğrusal olarak türetilir;
 * böylece öğleden gün batımına, alacakaranlıktan geceye geçiş kesintisiz olur.
 * Android sürümündeki ui/window/SkyPalette.kt ile aynı değerler.
 */

const rgb = hex => [(hex >> 16) & 255, (hex >> 8) & 255, hex & 255];

// Güneş yüksekliği büyükten küçüğe sıralanır.
const KEYFRAMES = [
  //          yükseklik  zenith    horizon   sea       land      cloudLit  cloudShade glow      parlaklık gece
  { elevation: 60, zenith: 0x14346e, horizon: 0x93c1e8, sea: 0x1e4b78, land: 0x3f5c3c, cloudLit: 0xffffff, cloudShade: 0xc6d6e6, glow: 0xfff3d8, glowStrength: 0.10, night: 0 },
  { elevation: 12, zenith: 0x1b4f9c, horizon: 0xaecEea, sea: 0x23507c, land: 0x425f3e, cloudLit: 0xf8fbff, cloudShade: 0xbacbdd, glow: 0xffe8b0, glowStrength: 0.22, night: 0 },
  { elevation: 3, zenith: 0x2b5286, horizon: 0xefb878, sea: 0x2a4a66, land: 0x4a4c34, cloudLit: 0xffe2c0, cloudShade: 0xb79178, glow: 0xffc073, glowStrength: 0.62, night: 0 },
  { elevation: -1, zenith: 0x1d2e56, horizon: 0xe8763e, sea: 0x1b2e42, land: 0x2e3128, cloudLit: 0xefae7c, cloudShade: 0x8a6252, glow: 0xff8a3c, glowStrength: 0.90, night: 0.15 },
  { elevation: -6, zenith: 0x101b3a, horizon: 0xb8452f, sea: 0x0e1826, land: 0x171a18, cloudLit: 0xae6e58, cloudShade: 0x5a3b36, glow: 0xe04e24, glowStrength: 0.72, night: 0.55 },
  { elevation: -12, zenith: 0x070c1d, horizon: 0x3e2340, sea: 0x070c14, land: 0x0c0e10, cloudLit: 0x4e3648, cloudShade: 0x241a24, glow: 0x7a2c3c, glowStrength: 0.34, night: 0.92 },
  { elevation: -18, zenith: 0x03060e, horizon: 0x0e1526, sea: 0x03050a, land: 0x070809, cloudLit: 0x171d29, cloudShade: 0x0b0e15, glow: 0x1a2038, glowStrength: 0.08, night: 1 },
  { elevation: -90, zenith: 0x02040a, horizon: 0x0a1020, sea: 0x020408, land: 0x060708, cloudLit: 0x121721, cloudShade: 0x090c12, glow: 0x141a2e, glowStrength: 0.05, night: 1 }
];

const COLOR_KEYS = ["zenith", "horizon", "sea", "land", "cloudLit", "cloudShade", "glow"];

const toPalette = k => {
  const out = { glowStrength: k.glowStrength, night: k.night };
  for (const key of COLOR_KEYS) out[key] = rgb(k[key]);
  return out;
};

const mix = (a, b, t) => [
  Math.round(a[0] + (b[0] - a[0]) * t),
  Math.round(a[1] + (b[1] - a[1]) * t),
  Math.round(a[2] + (b[2] - a[2]) * t)
];

function blend(a, b, t) {
  const out = {
    glowStrength: a.glowStrength + (b.glowStrength - a.glowStrength) * t,
    night: a.night + (b.night - a.night) * t
  };
  for (const key of COLOR_KEYS) out[key] = mix(a[key], b[key], t);
  return out;
}

/** Güneşin yerel yataydan yüksekliğine (derece) karşılık gelen renk takımı. */
export function paletteFor(sunElevationDeg) {
  if (sunElevationDeg >= KEYFRAMES[0].elevation) return toPalette(KEYFRAMES[0]);
  for (let i = 0; i < KEYFRAMES.length - 1; i++) {
    const a = KEYFRAMES[i], b = KEYFRAMES[i + 1];
    if (sunElevationDeg <= a.elevation && sunElevationDeg >= b.elevation) {
      const span = a.elevation - b.elevation;
      const t = span < 1e-9 ? 0 : (a.elevation - sunElevationDeg) / span;
      return blend(toPalette(a), toPalette(b), t);
    }
  }
  return toPalette(KEYFRAMES[KEYFRAMES.length - 1]);
}

/** [r,g,b] üçlüsünü canvas'ın anlayacağı dizeye çevirir. */
export const css = (c, alpha = 1) =>
  alpha >= 1 ? "rgb(" + c[0] + "," + c[1] + "," + c[2] + ")"
             : "rgba(" + c[0] + "," + c[1] + "," + c[2] + "," + alpha.toFixed(3) + ")";
