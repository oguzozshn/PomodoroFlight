/**
 * build/icon.png üretir (512×512, saydam zemin).
 * Bağımlılık yok: RGBA tampon + zlib ile elle PNG yazılır.
 * Çalıştır: npm run icon
 */
import zlib from "node:zlib";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const OUT = path.join(path.dirname(fileURLToPath(import.meta.url)), "..", "build", "icon.png");
const N = 512, SS = 3, W = N * SS;          // SS: süper örnekleme (kenar yumuşatma)
const R = W * 0.46, CX = W / 2, CY = W / 2;

const hex = (h) => [1, 3, 5].map((i) => parseInt(h.slice(i, i + 2), 16));
const GLOBE_A = hex("#122034"), GLOBE_B = hex("#070D17");
const LIMB = hex("#31445F"), GRAT = hex("#22334D");
const AMBER = hex("#F2B457"), AMBER_HI = hex("#FFD79A");

const buf = new Float32Array(W * W * 4);     // premultiplied olmayan RGBA, 0..1 alfa
const put = (x, y, [r, g, b], a) => {
  if (x < 0 || y < 0 || x >= W || y >= W || a <= 0) return;
  const i = (y * W + x) * 4, ia = 1 - a;
  buf[i] = buf[i] * ia + r * a;
  buf[i + 1] = buf[i + 1] * ia + g * a;
  buf[i + 2] = buf[i + 2] * ia + b * a;
  buf[i + 3] = buf[i + 3] * ia + 255 * a;
};
const disc = (cx, cy, rad, color, a = 1) => {
  for (let y = Math.floor(cy - rad); y <= cy + rad; y++)
    for (let x = Math.floor(cx - rad); x <= cx + rad; x++)
      if ((x - cx) ** 2 + (y - cy) ** 2 <= rad * rad) put(x, y, color, a);
};

/* küre gövdesi */
for (let y = 0; y < W; y++) {
  const t = y / W;
  const col = [0, 1, 2].map((k) => GLOBE_A[k] + (GLOBE_B[k] - GLOBE_A[k]) * t);
  for (let x = 0; x < W; x++) if ((x - CX) ** 2 + (y - CY) ** 2 <= R * R) put(x, y, col, 1);
}

/* paraleller: ortografik izdüşümde yatay kesitler */
const gw = W * 0.0035;
for (const lat of [-60, -30, 0, 30, 60]) {
  const p = (lat * Math.PI) / 180;
  const y = CY - R * Math.sin(p), half = R * Math.cos(p);
  for (let x = CX - half; x <= CX + half; x += 0.5) disc(x, y, gw, GRAT, lat === 0 ? 0.9 : 0.65);
}
/* meridyenler: yarı ekseni boylama göre daralan elipsler */
for (const lon of [-60, -30, 0, 30, 60]) {
  const a = R * Math.abs(Math.sin((lon * Math.PI) / 180));
  for (let s = -Math.PI / 2; s <= Math.PI / 2; s += 0.0015) {
    const y = CY - R * Math.sin(s), xo = a * Math.cos(s);
    disc(CX + xo, y, gw, GRAT, 0.6);
    if (a > 0.5) disc(CX - xo, y, gw, GRAT, 0.6);
  }
}

/* dış hat */
for (let th = 0; th < Math.PI * 2; th += 0.0006)
  disc(CX + R * Math.cos(th), CY + R * Math.sin(th), W * 0.006, LIMB, 0.95);

/* büyük daire rotası: sol alttan sağ üste kavisli geçiş */
const A = [CX - R * 0.80, CY + R * 0.34];
const B = [CX + R * 0.80, CY - R * 0.30];
const CP = [CX - R * 0.05, CY - R * 1.05];     // kontrol noktası: kutuptan geçen yay
const bez = (t) => [0, 1].map((k) =>
  (1 - t) ** 2 * A[k] + 2 * (1 - t) * t * CP[k] + t ** 2 * B[k]);

const sw = W * 0.019;
for (let t = 0; t <= 1; t += 0.0004) {
  const [x, y] = bez(t);
  if ((x - CX) ** 2 + (y - CY) ** 2 > (R - sw * 0.4) ** 2) continue;
  disc(x, y, sw, AMBER, t < 0.55 ? 0.5 : 1);   // kat edilen kısım dolu, kalanı soluk
}

/* kalkış damgası ve uçağın anlık konumu */
disc(...A, W * 0.028, AMBER, 0.55);
const P = bez(0.62);
disc(P[0], P[1], W * 0.062, [7, 13, 23], 1);
disc(P[0], P[1], W * 0.046, AMBER_HI, 1);

/* süper örneklemeyi indir */
const px = Buffer.alloc(N * N * 4);
for (let y = 0; y < N; y++)
  for (let x = 0; x < N; x++) {
    let r = 0, g = 0, b = 0, a = 0;
    for (let dy = 0; dy < SS; dy++)
      for (let dx = 0; dx < SS; dx++) {
        const i = ((y * SS + dy) * W + (x * SS + dx)) * 4;
        r += buf[i]; g += buf[i + 1]; b += buf[i + 2]; a += buf[i + 3];
      }
    const n = SS * SS, o = (y * N + x) * 4;
    px[o] = r / n; px[o + 1] = g / n; px[o + 2] = b / n; px[o + 3] = a / n;
  }

/* PNG yaz */
const raw = Buffer.alloc(N * (N * 4 + 1));
for (let y = 0; y < N; y++) {
  raw[y * (N * 4 + 1)] = 0;                                  // filtre: none
  px.copy(raw, y * (N * 4 + 1) + 1, y * N * 4, (y + 1) * N * 4);
}
const chunk = (type, data) => {
  const len = Buffer.alloc(4); len.writeUInt32BE(data.length);
  const body = Buffer.concat([Buffer.from(type, "ascii"), data]);
  const crc = Buffer.alloc(4); crc.writeUInt32BE(crc32(body) >>> 0);
  return Buffer.concat([len, body, crc]);
};
let TBL = null;
function crc32(b) {
  if (!TBL) {
    TBL = new Int32Array(256);
    for (let n = 0; n < 256; n++) { let c = n; for (let k = 0; k < 8; k++) c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1; TBL[n] = c; }
  }
  let c = -1;
  for (const byte of b) c = TBL[(c ^ byte) & 0xff] ^ (c >>> 8);
  return c ^ -1;
}
const ihdr = Buffer.alloc(13);
ihdr.writeUInt32BE(N, 0); ihdr.writeUInt32BE(N, 4);
ihdr[8] = 8; ihdr[9] = 6; ihdr[10] = 0; ihdr[11] = 0; ihdr[12] = 0;
fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, Buffer.concat([
  Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
  chunk("IHDR", ihdr),
  chunk("IDAT", zlib.deflateSync(raw, { level: 9 })),
  chunk("IEND", Buffer.alloc(0)),
]));
console.log("yazıldı:", OUT, fs.statSync(OUT).size, "bayt");
