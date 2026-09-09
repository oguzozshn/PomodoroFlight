/**
 * Uçuşa başlamadan önce kesilen biniş kartı.
 * Android sürümündeki data/Ticket.kt karşılığı.
 */

import { gateOf, remainingScheduleMinutes, focusMinutesFor } from "./estimates.js";
import { upperTr } from "./format.js";

const SEAT_ROWS = 38;
const SEAT_LETTERS = "ABCDEF";

/** Biniş ile kalkış arasındaki süre. */
export const BOARDING_MINUTES = 15;

/** Rotaya ve güne sabitlenmiş koltuk: aynı gün aynı rota aynı koltuğu verir. */
export function seatOf(routeKey, issuedAt) {
  const day = Math.floor(issuedAt / 86_400_000);
  let hash = day | 0;
  for (let i = 0; i < routeKey.length; i++) {
    hash = (hash * 31 + routeKey.charCodeAt(i)) & 0x7fffffff;
  }
  const row = 1 + (hash % SEAT_ROWS);
  const letter = SEAT_LETTERS[Math.floor(hash / SEAT_ROWS) % SEAT_LETTERS.length];
  return row + letter;
}

/**
 * Dekoratif barkod deseni: tohumdan türetilen, 1–3 birim arası değişen çizgi
 * genişlikleri. Gerçek bir barkod değildir, biletin görünümü içindir.
 */
export function barcodePattern(seed, count = 48) {
  let state = 0x9e3779b9 | 0;
  for (let i = 0; i < seed.length; i++) state = (Math.imul(state, 31) + seed.charCodeAt(i)) | 0;
  const out = new Array(count);
  for (let i = 0; i < count; i++) {
    state = (Math.imul(state, 1_103_515_245) + 12_345) | 0;
    out[i] = 1 + (((state >>> 16) & 0x7fff) % 3);
  }
  return out;
}

/** Yolcu adı boş bırakılmışsa biletin üzerinde "YOLCU" yazar. */
export const passengerOrDefault = name => upperTr((name ?? "").trim() || "Yolcu");

/**
 * Seçili rota ve ayarlardan bir bilet keser.
 * @param route  buildRoute() çıktısı
 * @param db     store.data
 * @param state  flightState() çıktısı
 */
export function issueTicket(route, db, state, now = Date.now()) {
  const scheduleMinutes = remainingScheduleMinutes(
    state.legsLeft, db.focusMin, db.breakMin, db.cycle
  );
  const flightNo = route.flightNo;
  const gate = gateOf(route.key);
  const seat = seatOf(route.key, now);

  return {
    route,
    passenger: passengerOrDefault(db.passenger),
    flightNo, gate, seat,
    boardingAt: now,
    departureAt: now + BOARDING_MINUTES * 60_000,
    arrivalAt: now + (BOARDING_MINUTES + scheduleMinutes) * 60_000,
    distanceKm: route.km,
    legs: state.legsLeft,
    focusMinutes: focusMinutesFor(state.legsLeft, db.focusMin),
    barcode: barcodePattern(flightNo + seat + gate)
  };
}
