/**
 * İki havalimanı arasındaki büyük daire rotası ve bunun pomodoro cinsinden
 * karşılığı. Android sürümündeki data/Route.kt karşılığı.
 *
 * Ölçek pomodoro sayısına değil süreye bağlı: bir rotayı bitirmek için gereken
 * toplam odak süresi pomodoro uzunluğundan bağımsızdır.
 */

import { registry } from "./places.js";
import {
  vec, ll, slerp, haversine, DEFAULT_FOCUS_MIN, KM_PER_MINUTE, KM_PER_POMODORO
} from "./geo.js";

export const routeKey = (o, d) => o + "-" + d;

/** Web sürümüyle aynı uçuş numarası üretimi: rotaya sabitlenmiş TKxxxx. */
export function flightNumber(key) {
  let n = 0;
  for (let i = 0; i < key.length; i++) n = (n * 31 + key.charCodeAt(i)) % 9000;
  return "TK" + (1000 + n);
}

/**
 * Rotayı bitirmek için kaç pomodoro gerektiği. Pomodoro ne kadar uzunsa
 * o kadar az bacak eder; toplam odak süresi aynı kalır.
 */
export const legsFor = (km, focusMin = DEFAULT_FOCUS_MIN) =>
  Math.max(1, Math.round(km / (KM_PER_MINUTE * focusMin)));

/** İki kod arasındaki büyük daire mesafesi; kodlar çözülemezse null. */
export function kmBetween(from, to) {
  const o = registry.find(from), d = registry.find(to);
  return o && d ? haversine(o.lat, o.lon, d.lat, d.lon) : null;
}

/** "IST-JFK" ya da (from, to) biçiminden tam rota nesnesi kurar. */
export function buildRoute(from, to, focusMin = DEFAULT_FOCUS_MIN) {
  if (to === undefined || typeof to === "number") {
    // tek anahtarla çağrıldı: buildRoute("IST-JFK", focusMin?)
    const parts = String(from).split("-");
    if (parts.length !== 2) return null;
    return buildRoute(parts[0], parts[1], to === undefined ? focusMin : to);
  }

  const o = registry.find(from);
  const d = registry.find(to);
  if (!o || !d || o.code === d.code) return null;

  const km = haversine(o.lat, o.lon, d.lat, d.lon);
  const a = vec(o.lat, o.lon);
  const b = vec(d.lat, d.lon);
  const key = routeKey(o.code, d.code);
  const legs = legsFor(km, focusMin);

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

/** Büyük daireyi n+1 noktalı bir çizgiye böler. */
export function samplePath(route, n = 240) {
  const out = new Array(n + 1);
  for (let i = 0; i <= n; i++) out[i] = pointAt(route, i / n);
  return out;
}

/**
 * Bacak sayısını kat edilen mesafeye çevirir; eski kayıtların göçürülmesi için.
 *
 * Bacak başına mesafe 850 km değil, rotanın kendi kmPerLeg değeri: bacak sayısı
 * yuvarlanarak bulunduğu için ikisi aynı değil. IST-JFK 8.026 km ve 9 bacak,
 * yani bacak başına 892 km. 850 ile çevirmek kullanıcının ilerlemesini sessizce
 * geri alırdı.
 */
export function kmForLegs(key, legs, focusMin = DEFAULT_FOCUS_MIN) {
  if (legs <= 0) return 0;
  const route = buildRoute(key, focusMin);
  if (!route) return legs * KM_PER_POMODORO;
  return Math.min(legs * route.kmPerLeg, route.km);
}
