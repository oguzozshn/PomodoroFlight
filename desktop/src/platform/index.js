/**
 * Uygulama ile işletim sistemi arasındaki tek yüzey.
 *
 * Köprüyü kabuk açar: masaüstünde Electron preload'u `window.desktop`,
 * mobilde Capacitor sarmalayıcısı `window.native` olarak koyacak.
 * İkisi de yoksa (düz tarayıcı) bütün çağrılar sessizce yok sayılır —
 * uygulama her koşulda çalışır, sadece native yetenekler eksilir.
 */

const bridge =
  (typeof window !== "undefined" && (window.desktop || window.native)) || null;

export const hasNative = bridge !== null;

export const platform = {
  /** Faz bitişlerinde işletim sistemi bildirimi. */
  notify(title, body) {
    bridge?.notify?.(title, body);
  },
  /** Görev çubuğu / kilit ekranı ilerlemesi: 0–1 oran, gizlemek için -1. */
  setProgress(value) {
    bridge?.setProgress?.(value);
  },
  /** Odak seyri sürerken ekranın uykuya dalmasını engeller. */
  setKeepAwake(on) {
    bridge?.setKeepAwake?.(Boolean(on));
  },
  /** Menü ve kısayollardan gelen komutlar: toggle | reset | skip | sound | clear */
  onCommand(fn) {
    bridge?.onCommand?.(fn);
  },
  /** Tema tercihi: system | light | dark | pink. Köprü yoksa hiç çağrılmaz. */
  onTheme(fn) {
    bridge?.onTheme?.(fn);
  },
  /** Tema tercihini kaydeder; sonuç onTheme ile geri gelir. */
  setTheme(mode) {
    bridge?.setTheme?.(mode);
  },
  /** Pencere görüntüsü için video seçtirir; köprü yoksa null. */
  pickVideo() {
    return bridge?.pickVideo?.() ?? Promise.resolve(null);
  }
};

let lastProgress, lastAwake;

/**
 * Durumu köprüye iteler. Aynı değeri tekrar göndermez —
 * çizim döngüsü saniyede onlarca kez çağırdığı için bu şart.
 */
export function syncPlatform({ progress, keepAwake }) {
  if (!bridge) return;
  if (progress !== lastProgress) {
    lastProgress = progress;
    platform.setProgress(progress);
  }
  if (keepAwake !== lastAwake) {
    lastAwake = keepAwake;
    platform.setKeepAwake(keepAwake);
  }
}
