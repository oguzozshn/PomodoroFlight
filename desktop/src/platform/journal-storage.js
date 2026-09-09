/**
 * Günlük dosyasının arkasındaki depolama.
 *
 * Masaüstünde userData altında gerçek bir metin dosyası — kullanıcı kendi
 * verisine bir düzenleyiciyle bakabilsin diye. Köprü yoksa (düz tarayıcı)
 * localStorage'a düşer; şema aynı, yalnızca taşıyıcı değişir.
 */

const KEY = "ucus-gunlugu-v1";

const bridge =
  (typeof window !== "undefined" && (window.desktop?.journal || window.native?.journal)) || null;

const fileStorage = {
  read: () => bridge.read(),
  append: line => bridge.append(line),
  clear: () => bridge.clear()
};

/* Tarayıcı yedeği: başlık ve sıkıştırma burada, çünkü ana süreç yok. */
const HEADER = "# ucus-gunlugu v1: zaman\trota\tdakika\tkm\tatlandi";
const KEEP = 50000;
const MAX_CHARS = 4 * 1024 * 1024;

const webStorage = {
  async read() {
    try { return localStorage.getItem(KEY) ?? ""; } catch { return ""; }
  },
  append(line) {
    try {
      const existing = localStorage.getItem(KEY) ?? "";
      let next = (existing ? existing : HEADER + "\n") + line + "\n";
      if (next.length > MAX_CHARS) {
        const lines = next.split("\n").filter(l => l && !l.startsWith("#"));
        next = HEADER + "\n" + lines.slice(-KEEP).join("\n") + "\n";
      }
      localStorage.setItem(KEY, next);
    } catch { /* kota dolu */ }
  },
  clear() {
    try { localStorage.removeItem(KEY); } catch { /* yok say */ }
  }
};

export const journalStorage = bridge ? fileStorage : webStorage;
