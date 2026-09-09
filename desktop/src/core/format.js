/**
 * Türkçe biçimlendirme. Android sürümündeki data/Format.kt karşılığı.
 */

const TR = "tr-TR";
const integerFormat = new Intl.NumberFormat(TR, { maximumFractionDigits: 0 });

/** Binlik ayracı Türkçe olan tam sayı gösterimi. */
export const formatNumber = value => integerFormat.format(Math.round(value));

/** Toplam odak süresi: "12,5 sa" */
export const formatHours = ms => (ms / 3_600_000).toFixed(1).replace(".", ",") + " sa";

export const formatDate = time =>
  new Date(time).toLocaleDateString(TR, { day: "2-digit", month: "2-digit", year: "numeric" });

/** Gün içindeki saat: "03:25" (ertesi güne taşıyorsa yanında gün farkı). */
export function formatTimeOfDay(time, now = Date.now()) {
  const clock = new Date(time).toLocaleTimeString(TR, { hour: "2-digit", minute: "2-digit", hour12: false });
  const days = Math.floor(time / 86_400_000) - Math.floor(now / 86_400_000);
  return days > 0 ? clock + " (+" + days + " gün)" : clock;
}

/**
 * Türkçe büyük harf.
 *
 * JavaScript'in `toUpperCase()` fonksiyonu yerelden bağımsız çalışır ve `i`
 * harfini `I` yapar. Türkçede karşılığı `İ`; aksi hâlde ekranda "BILET KES",
 * "HER ŞEYI SIL", "KAT EDILEN YOL" yazar.
 */
export const upperTr = text => String(text).toLocaleUpperCase(TR);

/** Yerelden bağımsız nokta ayraçlı sayı; günlük dosyası her makinede aynı okunmalı. */
export const decimalPoint = (value, digits = 1) => Number(value).toFixed(digits);
