/**
 * İki havalimanı arasındaki büyük daire rotası ve bunun pomodoro cinsinden
 * karşılığı. Android sürümündeki data/Route.kt karşılığı.
 *
 * Ölçek ayardan bağımsız: bir pomodoro 25 dakika ve gerçek seyir hızıyla
 * ~354 km (850 km/sa), dolayısıyla bir
 * rotanın kaç pomodoro ettiği odak süresi ayarıyla oynamaz. Ayar yalnız bir
 * oturumun kaç pomodoro taşıdığını belirler — 50 dakika iki, 37 dakika 1,48.
 */

import { registry } from "./places.js";
import { vec, ll, slerp, haversine, POMODORO_MIN, KM_PER_POMODORO } from "./geo.js";

export const routeKey = (o, d) => o + "-" + d;

/** Web sürümüyle aynı uçuş numarası üretimi: rotaya sabitlenmiş TKxxxx. */
export function flightNumber(key) {
  let n = 0;
  for (let i = 0; i < key.length; i++) n = (n * 31 + key.charCodeAt(i)) % 9000;
  return "TK" + (1000 + n);
}

/** Rotayı bitirmek için kaç pomodoro gerektiği; odak süresi ayarına bakmaz. */
export const legsFor = km => Math.max(1, Math.round(km / KM_PER_POMODORO));

/**
 * Bir odak oturumunun bu rotada kazandırdığı mesafe.
 *
 * Bacak başına mesafe nominal KM_PER_POMODORO değil rotanın kendi `kmPerLeg`
 * değeri — bacak sayısı yuvarlanarak bulunuyor (IST-JFK 8.026 km, 23 bacak,
 * 349 km/bacak).
 * Oturum bunun `focusMin / 25` katını taşır, böylece bacak sayısı kadar tam
 * pomodoro rotayı tastamam bitirir.
 */
export const kmForFocus = (route, focusMin) =>
  route ? route.kmPerLeg * (focusMin / POMODORO_MIN) : 0;

/** İki kod arasındaki büyük daire mesafesi; kodlar çözülemezse null. */
export function kmBetween(from, to) {
  const o = registry.find(from), d = registry.find(to);
  return o && d ? haversine(o.lat, o.lon, d.lat, d.lon) : null;
}

/** "IST-JFK" ya da (from, to) biçiminden tam rota nesnesi kurar. */
export function buildRoute(from, to) {
  if (to === undefined) {
    // tek anahtarla çağrıldı: buildRoute("IST-JFK")
    const parts = String(from).split("-");
    if (parts.length !== 2) return null;
    return buildRoute(parts[0], parts[1]);
  }

  const o = registry.find(from);
  const d = registry.find(to);
  if (!o || !d || o.code === d.code) return null;

  const km = haversine(o.lat, o.lon, d.lat, d.lon);
  const a = vec(o.lat, o.lon);
  const b = vec(d.lat, d.lon);
  const key = routeKey(o.code, d.code);
  const legs = legsFor(km);

  return {
    key, o: o.code, d: d.code,
    origin: o, dest: d,
    oName: o.city, dName: d.city,
    a, b, km, legs,
    kmPerLeg: km / legs,
    center: ll(slerp(a, b, 0.5)),
    flightNo: flightNumber(key)
  };
}

export const isValidPair = (from, to) =>
  Boolean(registry.find(from)) && Boolean(registry.find(to)) && from !== to;

/** Rota üzerinde t∈[0,1] oranındaki noktanın [enlem, boylam] değeri. */
export const pointAt = (route, t) => ll(slerp(route.a, route.b, t));

/**
 * Büyük daireyi n+1 noktalı bir çizgiye böler.
 * @param upTo Rotanın ne kadarı (0..1); yarım bırakılan uçuşun izi için.
 */
export function samplePath(route, n = 240, upTo = 1) {
  const out = new Array(n + 1);
  for (let i = 0; i <= n; i++) out[i] = pointAt(route, (i / n) * upTo);
  return out;
}

/**
 * Bacak sayısını kat edilen mesafeye çevirir; eski kayıtların göçürülmesi için.
 *
 * Burada da nominal KM_PER_POMODORO değil rotanın kendi kmPerLeg değeri
 * kullanılır; nominal değerle çevirmek ilerlemeyi sessizce kaydırırdı.
 */
export function kmForLegs(key, legs) {
  if (legs <= 0) return 0;
  const route = buildRoute(key);
  if (!route) return legs * KM_PER_POMODORO;
  return Math.min(legs * route.kmPerLeg, route.km);
}
