/**
 * Pomodoro faz makinesi.
 *
 * Tek gerçek kaynağı `endAt`: fazın biteceği mutlak an. Ekrandaki geri sayım
 * bunun türevi, tersi değil. Bu yüzden uygulama arkaya alınıp JavaScript
 * zamanlayıcısı dondurulsa bile `sync()` çağrısı doğru sonucu verir —
 * mobilde planlanacak yerel bildirim de aynı `endAt` değerine kurulur.
 *
 * DOM, ses ve platform bilgisi yoktur; sonuçları çağıran katman yorumlar.
 */

export const PHASES = ["focus", "break", "long"];

export function createTimer({ durationOf, canStart = () => true }) {
  const t = {
    phase: "focus",
    running: false,
    remaining: durationOf("focus"),
    endAt: 0
  };

  /** Sayacı çalıştırır. canStart engellerse false döner. */
  t.start = () => {
    if (t.running || !canStart(t.phase)) return false;
    t.running = true;
    t.endAt = Date.now() + t.remaining;
    return true;
  };

  /** Duraklatır; kalan süreyi dondurur. */
  t.pause = () => {
    if (t.running) t.remaining = Math.max(0, t.endAt - Date.now());
    t.running = false;
    t.endAt = 0;
  };

  t.toggle = () => (t.running ? (t.pause(), false) : t.start());

  /** Aynı fazı baştan başlatır. */
  t.reset = () => {
    t.pause();
    t.remaining = durationOf(t.phase);
  };

  /** Faz değiştirir ve duraklatılmış olarak yeni süreye kurar. */
  t.setPhase = phase => {
    t.phase = phase;
    t.remaining = durationOf(phase);
    t.running = false;
    t.endAt = 0;
  };

  /** Süre ayarı değişince, çalışmıyorsa kalan süreyi tazeler. */
  t.refreshDuration = () => {
    if (!t.running) t.remaining = durationOf(t.phase);
  };

  /**
   * Duvar saatiyle eşitler. Faz bu çağrıda dolduysa true döner —
   * kilit ekranında geçen süre de böyle telafi edilir.
   */
  t.sync = (now = Date.now()) => {
    if (!t.running) return false;
    t.remaining = t.endAt - now;
    if (t.remaining > 0) return false;
    t.remaining = 0;
    return true;
  };

  /** Fazın ne kadarı geçti, 0–1. */
  t.elapsed = () => {
    const d = durationOf(t.phase);
    return d > 0 ? Math.max(0, Math.min(1, 1 - t.remaining / d)) : 0;
  };

  return t;
}

/** mm:ss */
export function formatClock(ms) {
  const s = Math.max(0, Math.ceil(ms / 1000));
  return String(Math.floor(s / 60)).padStart(2, "0") + ":" + String(s % 60).padStart(2, "0");
}
