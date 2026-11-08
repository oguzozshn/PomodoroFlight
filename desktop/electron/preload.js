"use strict";

// Sayfa ile ana süreç arasındaki tek köprü. Renderer'a Node ya da ham ipcRenderer
// verilmez; yalnızca aşağıdaki uçlar açıktır.
const { contextBridge, ipcRenderer } = require("electron");

const clamp01 = (v) => (typeof v === "number" && v >= 0 && v <= 1 ? v : -1);

contextBridge.exposeInMainWorld("desktop", {
  /**
   * Faz bitişlerinde işletim sistemi bildirimi gösterir. `sound`, "Kabin sesi"
   * tercihidir: pencere arkadayken bildirimin kendi sesi ancak açıksa çalar.
   */
  notify: (title, body, sound) =>
    ipcRenderer.send("notify", String(title ?? ""), String(body ?? ""), Boolean(sound)),
  /**
   * Fazın dolacağı anı ana sürece bildirir; duraklamada 0. Pencere
   * küçültülünce sayfanın kendi döngüsü durduğu için bitişi ana süreç bekler.
   */
  schedulePhaseEnd: (at) => ipcRenderer.send("phase-schedule", Number(at) || 0),
  /** Ana süreç fazın dolduğunu haber verdiğinde çağrılır. */
  onPhaseDue: (fn) => {
    if (typeof fn !== "function") return;
    ipcRenderer.on("phase-due", () => fn());
  },
  /** Görev çubuğu ilerleme çubuğu: 0–1 arası oran, gizlemek için -1. */
  setProgress: (value) => ipcRenderer.send("progress", clamp01(value)),
  /** Odak seyri sürerken ekranın uykuya dalmasını engeller. */
  setKeepAwake: (on) => ipcRenderer.send("keep-awake", Boolean(on)),
  /** Menüden gelen komutlar: toggle | reset | skip | sound | clear */
  onCommand: (fn) => {
    if (typeof fn !== "function") return;
    ipcRenderer.on("cmd", (_e, cmd) => fn(String(cmd)));
  },

  /**
   * Uçuş günlüğü: userData altında düz metin bir dosya. Okuma asenkron —
   * senkron IPC, karşılığında dinleyici yoksa sayfayı sonsuza kadar bloke eder.
   * Dosya yönetimi (başlık, sıkıştırma) ana süreçte.
   */
  /**
   * Tema: system | light | dark | pink. Açılışta kayıtlı tercih bir kez,
   * menüden değiştikçe yeniden bildirilir.
   */
  onTheme: (fn) => {
    if (typeof fn !== "function") return;
    ipcRenderer.on("theme", (_e, mode) => fn(String(mode)));
    ipcRenderer.invoke("theme-get").then((mode) => fn(String(mode)));
  },
  /** Ayarlar penceresinden tema seçimi; ana süreç kaydeder, menüyü günceller. */
  setTheme: (mode) => ipcRenderer.send("set-theme", String(mode ?? "")),

  /** Pencere görüntüsü olarak bir video dosyası seçtirir; iptal edilirse null. */
  pickVideo: () => ipcRenderer.invoke("pick-video"),

  journal: {
    read: () => ipcRenderer.invoke("journal-read"),
    append: (line) => ipcRenderer.send("journal-append", String(line ?? "")),
    clear: () => ipcRenderer.send("journal-clear"),
  },
});
