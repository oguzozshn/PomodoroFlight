/**
 * Tamamlanan pomodoroların eklemeli günlüğü.
 *
 * Neden store.js içindeki JSON'a konmadı: o kayıt her duraklatmada baştan
 * yazılıyor; büyüyen bir listeyi oraya koymak her seferinde bütün geçmişi
 * yeniden yazmak olurdu. Burada ekleme dosyanın sonuna tek satır.
 *
 * Biçim sekmeyle ayrılmış düz metin. Depodaki UMC1/UMP2 ikili biçimlerinin
 * aksine burada okunabilirlik boyuttan önemli: bu kullanıcının kendi verisi ve
 * bir metin düzenleyiciyle bakılabilmesi işe yarıyor. Satır başına ~35 bayt,
 * günde 20 pomodoro için yılda ~250 KB.
 */

import { decimalPoint } from "./format.js";

export const JOURNAL_HEADER = "# ucus-gunlugu v1: zaman\trota\tdakika\tkm\tatlandi";

/** Yaklaşık yedi yıllık yoğun kullanım. */
const KEEP_RECORDS = 50_000;
const MAX_BYTES = 4 * 1024 * 1024;

/**
 * Tamamlanmış tek bir odak pomodorosu.
 * Sayaçların (toplam km, toplam süre) aksine burada zaman damgası var; günlük
 * dağılım, seri ve saat histogramı ancak böyle hesaplanabiliyor.
 */
export const focusRecord = ({ time, routeKey, minutes, km, skipped = false }) =>
  ({ time, routeKey, minutes, km, skipped });

/** Rotanın iki ucundaki havalimanı kodları. */
export const endpointsOf = record => record.routeKey.split("-").filter(Boolean);

export function encodeRecord(r) {
  return [r.time, r.routeKey, r.minutes, decimalPoint(r.km), r.skipped ? "1" : "0"].join("\t");
}

/** Bozuk ya da yarım yazılmış satırlar sessizce elenir, null döner. */
export function decodeRecord(line) {
  const trimmed = String(line).trim();
  if (!trimmed || trimmed.startsWith("#")) return null;
  const parts = trimmed.split("\t");
  if (parts.length < 5) return null;

  const time = Number(parts[0]);
  if (!Number.isFinite(time) || time <= 0) return null;
  const routeKey = parts[1];
  if (!routeKey.trim()) return null;
  const minutes = Number(parts[2]);
  const km = Number(parts[3]);
  if (!Number.isInteger(minutes) || minutes < 0) return null;
  if (!Number.isFinite(km) || km < 0) return null;

  return { time, routeKey, minutes, km, skipped: parts[4] === "1" };
}

/**
 * @param storage { read(): Promise<string>, append(line): void, clear(): void }
 *                Masaüstünde userData altındaki bir dosya (başlık ve sıkıştırma
 *                ana süreçte), tarayıcıda localStorage.
 */
export function createJournal(storage) {
  return {
    append(record) {
      try {
        storage.append(encodeRecord(record));
      } catch {
        // Günlük tutulamıyorsa sayaç çalışmaya devam etsin.
      }
    },

    /** Bütün kayıtlar, eskiden yeniye. Bozuk satırlar sessizce elenir. */
    async read() {
      try {
        const text = await storage.read();
        if (!text) return [];
        const out = [];
        for (const line of text.split("\n")) {
          const record = decodeRecord(line);
          if (record) out.push(record);
        }
        return out;
      } catch {
        return [];
      }
    },

    clear() {
      try { storage.clear(); } catch { /* bir sonraki denemede yine denenir */ }
    }
  };
}
