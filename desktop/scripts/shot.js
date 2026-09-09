/**
 * Geliştirme yardımcısı: pencereyi açar, sayfayı çizdirir ve
 * release/shot-<tema>.png dosyalarını yazıp çıkar. Uygulamanın kendisi değildir.
 * Çalıştır: npm run shot
 */
"use strict";
const { app, BrowserWindow, nativeTheme } = require("electron");
const path = require("node:path");
const fs = require("node:fs");
const { registerScheme, serve, START_URL } = require("../electron/serve.js");

registerScheme();

const OUT = path.join(__dirname, "..", "shots");
const wait = (ms) => new Promise((r) => setTimeout(r, ms));

app.whenReady().then(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  serve(path.join(__dirname, "..", "src"));
  const win = new BrowserWindow({
    width: 1180, height: 900, show: true,
    webPreferences: { preload: path.join(__dirname, "..", "electron", "preload.js"), contextIsolation: true },
  });
  const errors = [];
  // Electron 37+ tek bir details nesnesi gönderiyor; eski imza da destekleniyor.
  win.webContents.on("console-message", (a, b, c) => {
    if (a && typeof a === "object" && "level" in a) {
      if (a.level === "error" || a.level === "warning") errors.push(a.level + ": " + a.message);
    } else if (b >= 2) errors.push(String(c));
  });

  await win.loadURL(START_URL);

  for (const theme of ["dark", "light"]) {
    nativeTheme.themeSource = theme;
    await wait(1200);                                 // yazı tipleri + tema geçişi + ilk canvas karesi
    const seen = await win.webContents.executeJavaScript(
      `[matchMedia("(prefers-color-scheme: dark)").matches, getComputedStyle(document.documentElement).getPropertyValue("--ground").trim()]`);
    console.log("  tema=" + theme + " karanlıkMedya=" + seen[0] + " --ground=" + seen[1]);
    const img = await win.webContents.capturePage();
    fs.writeFileSync(path.join(OUT, `shot-${theme}.png`), img.toPNG());
    console.log("yazıldı: release/shot-" + theme + ".png");
  }

  const probe = await win.webContents.executeJavaScript(`(() => ({
    title: document.title,
    clock: document.querySelector("#clock").textContent,
    route: document.querySelector("#oCode").textContent + "-" + document.querySelector("#dCode").textContent,
    legs: document.querySelectorAll("#legs i").length,
    canvas: (() => { const c = document.querySelector("#map"); return c.width + "x" + c.height; })(),
    bridge: typeof window.desktop
  }))()`);
  console.log("durum:", JSON.stringify(probe));
  if (errors.length) console.log("konsol hataları:", errors);
  app.quit();
});
