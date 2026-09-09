/**
 * Ekranın ve bildirimin okuduğu türetilmiş değerler.
 * Android sürümündeki data/FlightUiState.kt karşılığı.
 *
 * İlerleme bacak sayısı değil km olarak saklanıyor: pomodoro uzunluğu
 * değiştiğinde rotanın bacak sayısı da değişir, "8 bacaktan 5'i" gibi bir kayıt
 * o an anlamsızlaşırdı. Kat edilen mesafe ise ayardan bağımsız.
 */

import { LONG_BREAK_MIN } from "./estimates.js";

/** Kayan noktalı toplamada varışın kaçmaması için birkaç metrelik pay. */
const ARRIVAL_EPSILON_KM = 0.001;

/**
 * @param db     store.data
 * @param route  buildRoute() çıktısı
 * @param timer  createTimer() çıktısı
 */
export function flightState(db, route, timer) {
  const km = route?.km ?? 0;
  const kmPerLeg = route?.kmPerLeg ?? 0;

  /** Bu rotada kat edilen mesafe. */
  const flownOnRoute = route
    ? Math.max(0, Math.min(km, db.progress[route.key] ?? 0))
    : 0;

  const arrived = route ? flownOnRoute >= km - ARRIVAL_EPSILON_KM : false;

  /** Tamamlanmış bacak sayısı; gösterim için mesafeden türetilir. */
  const done = kmPerLeg > 0
    ? Math.max(0, Math.min(route.legs, Math.floor(flownOnRoute / kmPerLeg + 1e-9)))
    : 0;

  /**
   * Bu rotada yol alınmış mı? "done > 0" ile sorulamaz: bacak sayısı odak
   * süresine bağlı olduğu için 25 dakikada bir bacak uçup 50 dakikaya geçince
   * done sıfıra düşer ve yarım kalmış uçuş hiç başlamamış gibi görünürdü.
   */
  const started = flownOnRoute > 0;

  /** Rotanın ne kadarı uçuldu (0..1) — devam eden pomodoro da orantılı sayılır. */
  let fraction = km <= 0 ? 0 : flownOnRoute / km;
  if (route && timer.phase === "focus" && !arrived) {
    fraction += timer.elapsed() * (kmPerLeg / km);
  }
  fraction = Math.max(0, Math.min(1, fraction));

  /** Ekrandaki irtifa göstergesi: kalkışta tırmanır, inişe doğru alçalır. */
  let altitudeM = 0;
  if (!arrived && fraction > 0) {
    const climb = Math.min(1, fraction / 0.06);
    const descend = Math.min(1, (1 - fraction) / 0.06);
    altitudeM = Math.floor((11300 * Math.min(climb, descend)) / 100) * 100;
  }

  return {
    flownOnRoute,
    done,
    arrived,
    started,
    legsLeft: route ? Math.max(0, route.legs - done) : 0,
    fraction,
    flownKm: km * fraction,
    remainingKm: Math.max(0, km - km * fraction),
    altitudeM
  };
}

/** Fazın süresi (ms). */
export const durationMsOf = (db, phase) =>
  (phase === "focus" ? db.focusMin : phase === "long" ? LONG_BREAK_MIN : db.breakMin) * 60000;
