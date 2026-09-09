/**
 * Kalıcı durum. Okuma/yazma tek bir adaptör arkasında duruyor.
 *
 * v2 ilerlemeyi **km** olarak tutuyor; v1 bacak sayısı tutuyordu. Eski kayıt
 * bulunursa bacaklar km'ye çevrilir — ama 850 ile değil, rotanın kendi bacak
 * uzunluğuyla: bacak sayısı yuvarlanarak bulunduğu için ikisi aynı değil ve
 * 850 ile çevirmek kullanıcının ilerlemesini sessizce geri alırdı.
 *
 * Sayacın kendisi de saklanıyor (phase/running/endAt): uygulama kapatılıp
 * açıldığında seyir kaldığı yerden sürüyor.
 */

import { kmForLegs } from "./route.js";
import { DEFAULT_FOCUS_MIN } from "./geo.js";

export const STORAGE_KEY = "ucus-modu-v2";

/** İlerlemeyi bacak sayısı olarak tutan eski kayıt. */
export const LEGACY_KEY = "ucus-modu-v1";

export const defaults = () => ({
  route: "IST-JFK",
  progress: {},      // rota anahtarı → o rotada kat edilen km
  log: [],           // { r: rota, km, t: zaman damgası }
  focusMin: DEFAULT_FOCUS_MIN,
  breakMin: 5,
  autoBreak: true,
  sound: true,
  passenger: "",
  ticketFor: "",   // bileti koparılmış rota; uçuş ancak koparılınca başlar
  windowMode: "realtime",   // pencereden bakış kipi: realtime | globe | video
  wingModel: "distant",     // pencereden görünen kanat: winglet | blended | plain | distant
  seatSide: "left",         // koltuk tarafı: pencere burnun soluna mı sağına mı bakıyor
  renderQuality: "normal",  // pencere sahnesinin çizim kalitesi: normal | high | ultra
  cycle: 0,          // 4'lük turda kaçıncı pomodoro
  totalMs: 0,
  totalKm: 0,
  phase: "focus",
  running: false,
  endAt: 0,
  remainingMs: DEFAULT_FOCUS_MIN * 60000
});

/** Tarayıcı / WebView adaptörü. Depolama kapalıysa sessizce belleğe düşer. */
export const localStorageAdapter = (key = STORAGE_KEY, legacyKey = LEGACY_KEY) => ({
  read() {
    try {
      const raw = localStorage.getItem(key);
      if (raw) return { data: JSON.parse(raw), legacy: false };
      const old = localStorage.getItem(legacyKey);
      if (old) return { data: JSON.parse(old), legacy: true };
      return null;
    } catch {
      return null;
    }
  },
  write(value) {
    try {
      localStorage.setItem(key, JSON.stringify(value));
    } catch {
      /* kota dolu ya da depolama kapalı — uçuş devam etsin */
    }
  }
});

/** Hiç yazmayan adaptör; test ve önizleme için. */
export const memoryAdapter = (seed = null, legacy = false) => {
  let held = seed;
  let isLegacy = legacy;
  return {
    read: () => (held ? { data: held, legacy: isLegacy } : null),
    write(v) { held = structuredClone(v); isLegacy = false; }
  };
};

/** Eski kaydın bacak sayılarını km'ye çevirir. */
function migrateProgress(progress, focusMin, legacy) {
  const out = {};
  for (const [key, value] of Object.entries(progress || {})) {
    const n = Number(value) || 0;
    out[key] = legacy ? kmForLegs(key, Math.round(n), focusMin) : n;
  }
  return out;
}

export function createStore(adapter = localStorageAdapter()) {
  const base = defaults();
  const stored = adapter.read();
  const data = { ...base, ...(stored?.data ?? {}) };

  data.focusMin = Number(data.focusMin) || base.focusMin;
  data.progress = migrateProgress(data.progress, data.focusMin, Boolean(stored?.legacy));
  data.log = Array.isArray(data.log) ? data.log : [];
  data.migratedFromLegs = Boolean(stored?.legacy);

  return {
    data,
    save() {
      const { migratedFromLegs, ...persist } = data;
      adapter.write(persist);
    },
    /** Günlüğü, ilerlemeyi ve istatistikleri sıfırlar; ayarlara dokunmaz. */
    clearProgress() {
      data.progress = {};
      data.log = [];
      data.ticketFor = "";   // baştan başlıyorsak bilet de yeniden kesilir
      data.cycle = 0;
      data.totalMs = 0;
      data.totalKm = 0;
      this.save();
    }
  };
}
