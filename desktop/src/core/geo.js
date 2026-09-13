/**
 * Küresel geometri. Tamamı saf fonksiyon — DOM, tuval ya da platform bilgisi yok,
 * dolayısıyla web, masaüstü ve mobilde aynen çalışır.
 */

export const RAD = Math.PI / 180;
export const DEG = 180 / Math.PI;
export const EARTH_KM = 6371;

/**
 * Bir pomodoronun uzunluğu (dakika). Ölçü birimi: odak süresi ayarı ne olursa
 * olsun 25 dakika bir pomodoro eder, 50 dakikalık bir oturum iki.
 */
export const POMODORO_MIN = 25;

/** Odak süresi ayarının varsayılanı — bir pomodoro kadar. */
export const DEFAULT_FOCUS_MIN = POMODORO_MIN;

/** Yolcu uçağının seyir hızı (km/sa). Ölçeğin ve uçuş süresi tahmininin kaynağı. */
export const CRUISE_KMH = 850;

/**
 * Bir pomodoronun taşıdığı nominal mesafe; rota bacaklarının ölçeği.
 *
 * Uçak gerçek seyir hızıyla gidiyor: 25 dakikada 850 km/sa ≈ 354 km. Eskiden
 * pomodoro başına 850 km'ydi, yani uçak gerçeğin 2,4 katı hızlıydı. Artık
 * rotanın odak süresi gerçek seyir süresine eşit; aradaki fark yalnız bacak
 * sayısının yuvarlanmasından (en fazla ±12,5 dk). Kalkış/iniş payı yok.
 */
export const KM_PER_POMODORO = CRUISE_KMH * POMODORO_MIN / 60;

/**
 * Odakta geçen her dakikanın taşıdığı mesafe (≈ 14,2 km).
 *
 * Pomodoro sabit olduğu için bu da sabit: 50 dakikalık bir oturum 25
 * dakikalığının iki katı yol alır, çünkü iki pomodoro eder.
 */
export const KM_PER_MINUTE = KM_PER_POMODORO / POMODORO_MIN;

/** Enlem-boylamı birim küre üzerinde bir vektöre çevirir. */
export const vec = (la, lo) => {
  const p = la * RAD, l = lo * RAD;
  return [Math.cos(p) * Math.cos(l), Math.cos(p) * Math.sin(l), Math.sin(p)];
};

/** Birim vektörü [enlem, boylam] derecesine çevirir. */
export const ll = v => [
  Math.asin(Math.max(-1, Math.min(1, v[2]))) * DEG,
  Math.atan2(v[1], v[0]) * DEG
];

/** İki vektör arasında küresel doğrusal ara değer — büyük daire üzerinde ilerler. */
export function slerp(a, b, t) {
  const d = Math.max(-1, Math.min(1, a[0] * b[0] + a[1] * b[1] + a[2] * b[2]));
  const w = Math.acos(d);
  if (w < 1e-8) return a.slice();
  const s = Math.sin(w), k1 = Math.sin((1 - t) * w) / s, k2 = Math.sin(t * w) / s;
  return [a[0] * k1 + b[0] * k2, a[1] * k1 + b[1] * k2, a[2] * k1 + b[2] * k2];
}

/** İki nokta arasındaki büyük daire mesafesi, km. */
export function haversine(la1, lo1, la2, lo2) {
  const dLa = (la2 - la1) * RAD, dLo = (lo2 - lo1) * RAD;
  const h = Math.sin(dLa / 2) ** 2 +
            Math.cos(la1 * RAD) * Math.cos(la2 * RAD) * Math.sin(dLo / 2) ** 2;
  return 2 * EARTH_KM * Math.asin(Math.sqrt(h));
}

/** Boylamı (-180, 180] aralığına sarar. */
export const wrapLon = d => ((d % 360) + 540) % 360 - 180;

/**
 * Ortografik izdüşüm — küreye uzaktan bakış.
 * c: { la, lo } bakış merkezi, { cx, cy } ekran merkezi, { r } yarıçap.
 * vis=false ise nokta kürenin arkasında kalıyordur.
 */
export function project(la, lo, c) {
  const p = la * RAD, l = lo * RAD, p0 = c.la * RAD, l0 = c.lo * RAD;
  const cosc = Math.sin(p0) * Math.sin(p) + Math.cos(p0) * Math.cos(p) * Math.cos(l - l0);
  return {
    x: c.cx + c.r * Math.cos(p) * Math.sin(l - l0),
    y: c.cy - c.r * (Math.cos(p0) * Math.sin(p) - Math.sin(p0) * Math.cos(p) * Math.cos(l - l0)),
    vis: cosc >= 0
  };
}

/** Bir noktadan diğerine bakarken kuzeyden ölçülen ilk yön açısı (derece, 0..360). */
export function bearing(la1, lo1, la2, lo2) {
  const p1 = la1 * RAD, p2 = la2 * RAD, dLon = (lo2 - lo1) * RAD;
  const y = Math.sin(dLon) * Math.cos(p2);
  const x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dLon);
  return (Math.atan2(y, x) * DEG + 360) % 360;
}
