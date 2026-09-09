/**
 * Günlük kayıtlarından çıkan özetler.
 *
 * Tamamı saf: ne DOM'a ne dosyaya bağlı, o yüzden doğrudan ölçülebiliyor.
 * Gün sınırı makinenin yerel saatine göre çizilir.
 */

/** Atlanan pomodorolar odak istatistiklerine katılmaz. */
export const counted = records => records.filter(r => !r.skipped);

/** Yerel gece yarısına yuvarlanmış tarih. */
export function startOfDay(time) {
  const d = new Date(time);
  d.setHours(0, 0, 0, 0);
  return d;
}

/** Karşılaştırma ve sözlük anahtarı olarak kullanılan yerel tarih: "2026-09-09". */
export function dayKey(time) {
  const d = startOfDay(time);
  return d.getFullYear() + "-" +
    String(d.getMonth() + 1).padStart(2, "0") + "-" +
    String(d.getDate()).padStart(2, "0");
}

export function addDays(time, n) {
  const d = startOfDay(time);
  d.setDate(d.getDate() + n);
  return d.getTime();
}

function bucketByDay(records) {
  const buckets = new Map();
  for (const r of counted(records)) {
    const key = dayKey(r.time);
    buckets.set(key, (buckets.get(key) ?? 0) + 1);
  }
  return buckets;
}

/** Bugün dâhil son `days` günün sayımı; liste eskiden yeniye sıralıdır. */
export function dailyCounts(records, days, today = Date.now()) {
  const buckets = bucketByDay(records);
  const out = [];
  for (let i = 0; i < days; i++) {
    const time = addDays(today, -(days - 1 - i));
    out.push({ time, key: dayKey(time), count: buckets.get(dayKey(time)) ?? 0 });
  }
  return out;
}

/**
 * Üst üste kaç gün. Bugün boş olabilir — dün çalışılmışsa seri bozulmamış
 * sayılır, gün bitmediği için.
 */
export function streak(records, today = Date.now()) {
  const days = new Set(counted(records).map(r => dayKey(r.time)));
  if (days.size === 0) return 0;

  let cursor = days.has(dayKey(today)) ? startOfDay(today).getTime() : addDays(today, -1);
  if (!days.has(dayKey(cursor))) return 0;

  let length = 0;
  while (days.has(dayKey(cursor))) {
    length++;
    cursor = addDays(cursor, -1);
  }
  return length;
}

/** Günün hangi saatlerinde çalışıldığı; 24 kutu. */
export function hourHistogram(records) {
  const hours = new Array(24).fill(0);
  for (const r of counted(records)) hours[new Date(r.time).getHours()]++;
  return hours;
}

/** En yoğun ardışık iki saat; histogramda vurgulanan aralık. */
export function peakWindow(hours) {
  if (hours.reduce((a, b) => a + b, 0) === 0) return null;
  let best = 0, bestTotal = -1;
  for (let h = 0; h < 24; h++) {
    const total = hours[h] + hours[(h + 1) % 24];
    if (total > bestTotal) { bestTotal = total; best = h; }
  }
  return best;
}

export function topRoutes(records, limit) {
  const counts = new Map();
  for (const r of counted(records)) counts.set(r.routeKey, (counts.get(r.routeKey) ?? 0) + 1);
  return [...counts.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))
    .slice(0, limit)
    .map(([routeKey, count]) => ({ routeKey, count }));
}

export function busiestDay(records) {
  const buckets = bucketByDay(records);
  if (buckets.size === 0) return null;
  const [key, count] = [...buckets.entries()]
    .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))[0];
  return { key, count };
}

/** Hiç olmazsa bir pomodoro yapılmış gün sayısı. */
export const activeDays = records =>
  new Set(counted(records).map(r => dayKey(r.time))).size;

export const focusMinutes = records =>
  counted(records).reduce((sum, r) => sum + r.minutes, 0);

/** Uğranılan havalimanı kodları; rotanın iki ucu da sayılır. */
export function airports(records) {
  const codes = new Set();
  for (const r of counted(records)) {
    for (const code of r.routeKey.split("-")) if (code) codes.add(code);
  }
  return codes;
}

/** Kaydın başladığı an; ekranın altında "şu tarihten beri" yazmak için. */
export function firstRecordTime(records) {
  let min = Infinity;
  for (const r of records) if (r.time < min) min = r.time;
  return Number.isFinite(min) ? min : null;
}
