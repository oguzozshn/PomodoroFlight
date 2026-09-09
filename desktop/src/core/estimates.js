/**
 * Uçuş tahminleri ve biçimlendirme yardımcıları.
 * Android sürümündeki data/Estimates.kt karşılığı.
 */

/** Yolcu uçağının seyir hızı; uçuş süresi tahmininde kullanılır. */
const CRUISE_KMH = 850;

/** Manevra, taksi ve tırmanış için sabit pay (dakika). */
const GROUND_MINUTES = 30;

/** Uzun molanın süresi (dakika). */
export const LONG_BREAK_MIN = 15;

/** Rotanın gerçek hayattaki kaba uçuş süresi (dakika). */
export const realFlightMinutes = km => Math.round((km / CRUISE_KMH) * 60) + GROUND_MINUTES;

/** Rotayı bitirmek için gereken toplam odak süresi (dakika). */
export const focusMinutesFor = (legs, focusMin) => legs * focusMin;

/** 585 → "9 sa 45 dk", 45 → "45 dk" */
export function formatDuration(minutes) {
  const h = Math.floor(minutes / 60), m = minutes % 60;
  if (h === 0) return m + " dk";
  if (m === 0) return h + " sa";
  return h + " sa " + m + " dk";
}

/**
 * Rotanın kalanını bitirmek için gereken takvim süresi: odaklar + aralarındaki
 * molalar. Dördüncü pomodorodan sonraki uzun mola da hesaba katılır.
 */
export function remainingScheduleMinutes(legsLeft, focusMin, breakMin, cycle) {
  if (legsLeft <= 0) return 0;
  let total = legsLeft * focusMin;
  for (let i = 1; i < legsLeft; i++) {
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
