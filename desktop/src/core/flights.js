/**
 * Uçuş kaydı: aynı anda tek bir etkin uçuş, biten her uçuş günlükte bir satır.
 *
 * Eskiden ilerleme rota başına bir km sayacındaydı; bir rotadan çıkıp dönünce
 * uçak kaldığı yerden devam ediyor, yarıda bırakılan uçuş hiçbir listede
 * görünmüyordu. Artık yolculuk bilet koparılınca başlıyor, varışla ya da
 * "Bitir" ile kapanıyor ve ne kadarı uçulduysa onunla günlüğe düşüyor.
 *
 * Etkin uçuş: { r, flown (km), start, landed? }
 *   landed — varılmış uçuş. Satırı günlüğe yazıldı ama ekran "İniş yapıldı /
 *   Tekrar uç" durumunu gösterebilsin diye yeni uçuş başlayana kadar duruyor.
 * Günlük satırı: { r, km, flown, start, t, done }
 *
 * DOM'a bağlı değil; doğrudan ölçülebiliyor.
 */

import { buildRoute, samplePath } from "./route.js";

const round1 = x => Math.round(x * 10) / 10;

/** Yeni, sıfırdan bir uçuş başlatır. Varsa önceki uçuşun yerini alır. */
export function startFlight(db, key, now = Date.now()) {
  db.flight = { r: key, flown: 0, start: now };
  return db.flight;
}

/**
 * Etkin uçuşu kapatır.
 *  done: true  → varış. Satır yazılır, uçuş `landed` olarak ekranda kalır.
 *  done: false → yarıda bırakma. Uçulan yol varsa satır yazılır, uçuş silinir;
 *                sıfır km'lik uçuş (bilet koparılıp oturum bitmeden) iz bırakmaz.
 * @returns yazılan satır ya da null
 */
export function endFlight(db, route, { done, now = Date.now() } = {}) {
  const f = db.flight;
  if (!f || !route || f.r !== route.key || f.landed) return null;

  if (done) {
    const row = { r: route.key, km: Math.round(route.km), flown: Math.round(route.km),
      start: f.start ?? now, t: now, done: true };
    db.log.unshift(row);
    f.flown = route.km;
    f.landed = now;
    return row;
  }

  db.flight = null;
  if (!(f.flown > 0)) return null;
  const row = { r: route.key, km: Math.round(route.km), flown: round1(f.flown),
    start: f.start ?? now, t: now, done: false };
  db.log.unshift(row);
  return row;
}

/**
 * Günlük satırını ortak biçime getirir. Eski sürümün `{ r, km, t }` satırları
 * yalnız varışta yazıldığı için tamamlanmış sayılır. `km` bilinmiyorsa null
 * kalır (göçten gelen yarım satırlar); gösterirken rotadan tamamlanır.
 */
export function logEntryOf(raw) {
  const done = raw.done !== false;
  const km = Number.isFinite(raw.km) ? raw.km : null;
  const flown = Number.isFinite(raw.flown) ? raw.flown : (done && km !== null ? km : 0);
  return { r: raw.r, km, flown, start: raw.start ?? raw.t, t: raw.t, done };
}

export const completedFlights = log => log.filter(raw => logEntryOf(raw).done);
export const partialFlights = log => log.filter(raw => !logEntryOf(raw).done);

/**
 * Küre üzerindeki izler: tamamlanan uçuş tam yay, yarım uçuş yalnız uçulan
 * kısım. Aynı rota birden çok kez uçulduysa en uzun iz çizilir.
 */
export function flightTrails(log, n = 96) {
  const extent = new Map();
  for (const raw of log) {
    const e = logEntryOf(raw);
    const route = buildRoute(e.r);
    if (!route) continue;
    const t = e.done ? 1 : Math.max(0, Math.min(1, e.flown / route.km));
    if (t <= 0) continue;
    const prev = extent.get(e.r);
    if (!prev || prev.t < t) extent.set(e.r, { route, t });
  }
  return [...extent.values()].map(({ route, t }) =>
    samplePath(route, Math.max(2, Math.round(n * t)), t));
}

/** Kayıttan okunan etkin uçuş geçerli mi; değilse null. */
export function validFlight(f) {
  if (!f || typeof f !== "object" || typeof f.r !== "string") return null;
  const flown = Number(f.flown);
  if (!Number.isFinite(flown) || flown < 0) return null;
  return { ...f, flown };
}

/**
 * Göç (bir kez): rota başına ilerlemeyi tek etkin uçuşa çevirir.
 *  - Açık rotanın ilerlemesi (ya da bileti koparılmış açık rota) etkin uçuş olur.
 *  - Diğer rotalardaki yarım ilerlemeler günlüğe yarım satır olarak eklenir.
 *  - Günlükte varışı zaten yazılmış tam ilerlemeler atlanır, çift satır olmaz.
 * Yükleme anında yer sözlüğü inmediği için rota uzunluğu bilinmiyor; yarım
 * satırların `km`'si boş kalır.
 */
export function flightFromProgress({ progress, route, ticketFor, log, now = Date.now() }) {
  const out = { flight: null, log: Array.isArray(log) ? log.slice() : [] };
  const landedKm = new Map();
  for (const raw of out.log) {
    const e = logEntryOf(raw);
    if (e.done && e.km !== null) landedKm.set(e.r, e.km);
  }

  for (const [key, value] of Object.entries(progress || {})) {
    const flown = Number(value) || 0;
    const landed = landedKm.has(key) && Math.abs(landedKm.get(key) - flown) <= 1;
    if (landed || flown <= 0) continue;
    if (key === route) out.flight = { r: key, flown, start: null };
    else out.log.push({ r: key, km: null, flown: round1(flown), start: null, t: now, done: false });
  }

  if (!out.flight && ticketFor && ticketFor === route) out.flight = { r: route, flown: 0, start: null };
  return out;
}
