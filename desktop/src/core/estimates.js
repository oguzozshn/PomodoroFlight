/**
 * Uçuş tahminleri ve biçimlendirme yardımcıları.
 * Android sürümündeki data/Estimates.kt karşılığı.
 */

import { POMODORO_MIN, CRUISE_KMH } from "./geo.js";

/** Uzun molanın süresi (dakika). */
export const LONG_BREAK_MIN = 15;

/**
 * Rotanın gerçek seyir hızıyla süresi (dakika). Kalkış, taksi ve iniş dahil
 * değil — pomodoro ölçeğiyle aynı tanım, bu yüzden odak süresinden yalnız
 * 25 dakikaya yuvarlama kadar ayrılır.
 */
export const cruiseMinutes = km => Math.round((km / CRUISE_KMH) * 60);

/** Rotayı bitirmek için gereken toplam odak süresi (dakika). */
export const focusMinutesFor = legs => legs * POMODORO_MIN;

/** 585 → "9 sa 45 dk", 45 → "45 dk" */
export function formatDuration(minutes) {
  const h = Math.floor(minutes / 60), m = minutes % 60;
  if (h === 0) return m + " dk";
  if (m === 0) return h + " sa";
  return h + " sa " + m + " dk";
}

/**
 * Rotanın kalanını bitirmek için gereken takvim süresi: odaklar + aralarındaki
 * molalar. Mola sayısı pomodoro sayısına değil oturum sayısına bağlı — 50
 * dakikalık oturumda iki pomodoro arasına mola girmez. Dördüncü oturumdan
 * sonraki uzun mola da hesaba katılır.
 */
export function remainingScheduleMinutes(legsLeft, focusMin, breakMin, cycle) {
  if (legsLeft <= 0) return 0;
  const sessions = Math.max(1, Math.ceil((legsLeft * POMODORO_MIN) / Math.max(1, focusMin)));
  let total = legsLeft * POMODORO_MIN;
  for (let i = 1; i < sessions; i++) {
    total += (cycle + i) % 4 === 0 ? LONG_BREAK_MIN : breakMin;
  }
  return total;
}

/** Rotaya sabitlenmiş, her açılışta aynı çıkan kapı numarası. */
export function gateOf(key) {
  let n = 0;
  for (let i = 0; i < key.length; i++) n = (n * 31 + key.charCodeAt(i)) & 0x7fffffff;
  return "ABCD"[n % 4] + (1 + (Math.floor(n / 4) % 30));
}

/** Dünyanın ekvator çevresi; toplam yolun kaç tur ettiğini ölçmek için. */
export const EARTH_CIRCUMFERENCE_KM = 40_075;

/** Toplam kat edilen yolun kaç dünya turu ettiği. */
export const worldLaps = km => km / EARTH_CIRCUMFERENCE_KM;

/** Süren turun tamamlanma oranı (0..1). */
export function currentLapFraction(km) {
  if (km <= 0) return 0;
  const laps = worldLaps(km);
  return laps - Math.floor(laps);
}
