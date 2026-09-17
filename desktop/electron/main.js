"use strict";

const { app, BrowserWindow, Menu, Notification, ipcMain, shell, nativeTheme, powerSaveBlocker } =
  require("electron");
const path = require("node:path");
const fs = require("node:fs");
const { registerScheme, serve, START_URL } = require("./serve.js");
const { registerJournalIpc } = require("./journal.js");
const { registerMediaIpc } = require("./media.js");

// Şema kaydı app hazır olmadan önce yapılmalı.
registerScheme();

// Windows bildirimlerinin uygulama adıyla görünmesi için gerekli.
app.setAppUserModelId("com.oguzozsahin.ucusmodu");

const ROOT = path.join(__dirname, "..");
const SRC = path.join(ROOT, "src");
const ICON = path.join(ROOT, "build", "icon.png");

/* ---------------- pencere durumu ---------------- */
// Konum/boyut ve tema tercihi userData altında küçük bir JSON'da tutulur;
// uçuş verisinin kendisi renderer'daki localStorage'da (aynı profil klasöründe) durur.
const STATE_FILE = () => path.join(app.getPath("userData"), "window-state.json");
const DEFAULT_STATE = { width: 1200, height: 900, x: undefined, y: undefined, maximized: false, theme: "system" };
let state = { ...DEFAULT_STATE };

function loadState() {
  try { Object.assign(state, JSON.parse(fs.readFileSync(STATE_FILE(), "utf8"))); } catch { /* ilk açılış */ }
}
function saveState() {
  try { fs.writeFileSync(STATE_FILE(), JSON.stringify(state, null, 2)); } catch { /* yazılamadı, önemsiz */ }
}

/* ---------------- uyanık tut ---------------- */
// Odak seyri sürerken ekran uykuya dalmasın; mola/duraklamada bırakılır.
let blockerId = null;
function keepAwake(on) {
  if (on && blockerId === null) blockerId = powerSaveBlocker.start("prevent-display-sleep");
  else if (!on && blockerId !== null) {
    if (powerSaveBlocker.isStarted(blockerId)) powerSaveBlocker.stop(blockerId);
    blockerId = null;
  }
}

/* ---------------- pencere ---------------- */
let win = null;

function createWindow() {
  win = new BrowserWindow({
    width: state.width,
    height: state.height,
    x: state.x,
    y: state.y,
    minWidth: 880,
    minHeight: 640,
    show: false,
    backgroundColor: backgroundFor(state.theme),
    icon: fs.existsSync(ICON) ? ICON : undefined,
    title: "Uçuş Modu",
    autoHideMenuBar: false,
    webPreferences: {
      preload: path.join(__dirname, "preload.js"),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: true,
      spellcheck: false,
    },
  });

  if (state.maximized) win.maximize();
  win.once("ready-to-show", () => win.show());
  win.loadURL(START_URL);

  const remember = () => {
    if (!win || win.isDestroyed() || win.isMinimized()) return;
    state.maximized = win.isMaximized();
    if (!state.maximized) {
      const b = win.getBounds();
      Object.assign(state, { x: b.x, y: b.y, width: b.width, height: b.height });
    }
  };
  win.on("resize", remember);
  win.on("move", remember);
  win.on("close", () => { remember(); saveState(); });
  win.on("closed", () => { win = null; });

  // Dış bağlantılar sistem tarayıcısında açılsın, uygulama penceresi rotadan çıkmasın.
  win.webContents.setWindowOpenHandler(({ url }) => {
    if (/^https?:/.test(url)) shell.openExternal(url);
    return { action: "deny" };
  });
  win.webContents.on("will-navigate", (e, url) => {
    if (url !== win.webContents.getURL()) {
      e.preventDefault();
      if (/^https?:/.test(url)) shell.openExternal(url);
    }
  });
}

/* ---------------- menü ---------------- */
const send = (cmd) => { if (win && !win.isDestroyed()) win.webContents.send("cmd", cmd); };

/* Tema: system | light | dark | pink. Pembenin işletim sisteminde karşılığı
   yok: sistem açık temaya alınır (kaydırma çubukları, diyaloglar), pembe palet
   sayfaya data-theme özniteliğiyle konur. */
const nativeSource = (mode) => (mode === "pink" ? "light" : mode);

function backgroundFor(mode) {
  if (mode === "pink") return "#FBEFF4";
  return nativeTheme.shouldUseDarkColors ? "#080E18" : "#EDF0F4";
}

function setTheme(mode) {
  state.theme = mode;
  nativeTheme.themeSource = nativeSource(mode);
  if (win && !win.isDestroyed()) {
    win.setBackgroundColor(backgroundFor(mode));
    win.webContents.send("theme", mode);
  }
  saveState();
}

function confirmClear() {
  const { dialog } = require("electron");
  const r = dialog.showMessageBoxSync(win, {
    type: "warning",
    title: "Günlüğü temizle",
    message: "Tüm uçuş günlüğü ve ilerleme silinsin mi?",
    detail: "Tamamlanan rotalar, toplam odak süresi ve kat edilen yol sıfırlanır. Bu işlem geri alınamaz.",
    buttons: ["Vazgeç", "Temizle"],
    defaultId: 0,
    cancelId: 0,
  });
  if (r === 1) send("clear");
}

function buildMenu() {
  const themeItem = (label, mode) => ({
    label, type: "radio", checked: state.theme === mode, click: () => setTheme(mode),
  });

  Menu.setApplicationMenu(Menu.buildFromTemplate([
    {
      label: "Uçuş",
      submenu: [
        { label: "Başlat / Beklet", accelerator: "CmdOrCtrl+Space", click: () => send("toggle") },
        { label: "Sayacı sıfırla", accelerator: "CmdOrCtrl+R", click: () => send("reset") },
        { label: "Uçuşu bitir…", accelerator: "CmdOrCtrl+E", click: () => send("end") },
        { label: "Molayı atla", accelerator: "CmdOrCtrl+Right", click: () => send("skip") },
        { type: "separator" },
        { label: "Kabin sesi", accelerator: "CmdOrCtrl+M", click: () => send("sound") },
        { label: "Günlüğü temizle…", click: () => confirmClear() },
        { type: "separator" },
        { role: "quit", label: "Çıkış" },
      ],
    },
    { label: "Ayarlar", click: () => send("settings") },
    { label: "Uçuş günlüğü", click: () => send("log") },
    { label: "İstatistikler", click: () => send("stats") },
    {
      label: "Görünüm",
      submenu: [
        themeItem("Sistem teması", "system"),
        themeItem("Açık", "light"),
        themeItem("Koyu", "dark"),
        themeItem("Pembe", "pink"),
        { type: "separator" },
        { role: "resetZoom", label: "Yakınlaştırmayı sıfırla" },
        { role: "zoomIn", label: "Yakınlaştır" },
        { role: "zoomOut", label: "Uzaklaştır" },
        { type: "separator" },
        { role: "togglefullscreen", label: "Tam ekran" },
        { role: "toggleDevTools", label: "Geliştirici araçları" },
      ],
    },
    {
      label: "Yardım",
      submenu: [
        {
          label: "Veri klasörünü aç",
          click: () => shell.openPath(app.getPath("userData")),
        },
        {
          label: "Hakkında",
          click: () => {
            const { dialog } = require("electron");
            dialog.showMessageBox(win, {
              type: "info",
              title: "Uçuş Modu hakkında",
              message: "Uçuş Modu " + app.getVersion(),
              detail:
                "Rota tabanlı pomodoro sayacı.\nBir pomodoro 25 dk · 850 km/sa seyirle ≈ 354 km.\n\n" +
                "Electron " + process.versions.electron + " · Chromium " + process.versions.chrome,
              buttons: ["Tamam"],
            });
          },
        },
      ],
    },
  ]));
}

/* ---------------- IPC ---------------- */
ipcMain.on("notify", (_e, title, body) => {
  if (!Notification.isSupported()) return;
  const n = new Notification({
    title: String(title || "Uçuş Modu"),
    body: String(body || ""),
    icon: fs.existsSync(ICON) ? ICON : undefined,
    silent: true, // ses zaten kabin çanıyla veriliyor
  });
  n.on("click", () => { if (win && !win.isDestroyed()) { win.show(); win.focus(); } });
  n.show();
});

ipcMain.on("progress", (_e, value) => {
  if (win && !win.isDestroyed()) win.setProgressBar(typeof value === "number" ? value : -1);
});

ipcMain.on("keep-awake", (_e, on) => keepAwake(Boolean(on)));

ipcMain.handle("theme-get", () => state.theme);

const THEMES = ["system", "light", "dark", "pink"];
ipcMain.on("set-theme", (_e, mode) => {
  if (!THEMES.includes(mode)) return;
  setTheme(mode);
  buildMenu();                                  // menüdeki radyo işareti de değişsin
});


/* ---------------- yaşam döngüsü ---------------- */
if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on("second-instance", () => {
    if (win && !win.isDestroyed()) { if (win.isMinimized()) win.restore(); win.focus(); }
  });

  app.whenReady().then(() => {
    loadState();
    serve(SRC);
    registerJournalIpc();
    registerMediaIpc(() => win);
    nativeTheme.themeSource = nativeSource(state.theme);
    buildMenu();
    createWindow();
    app.on("activate", () => { if (BrowserWindow.getAllWindows().length === 0) createWindow(); });
  });

  app.on("window-all-closed", () => { if (process.platform !== "darwin") app.quit(); });
  app.on("before-quit", () => { keepAwake(false); saveState(); });
}
