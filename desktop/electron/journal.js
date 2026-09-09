"use strict";

/**
 * Uçuş günlüğü: userData altında düz metin bir dosya.
 *
 * Kullanıcının kendi verisi olduğu için okunabilirlik boyuttan önemli — bir
 * metin düzenleyiciyle bakılabilmesi işe yarıyor. Dosya yönetiminin tamamı
 * burada; sayfa yalnızca tek satır gönderir.
 *
 * Okuma `handle` ile asenkron: senkron IPC, karşılığında dinleyici yoksa
 * render sürecini sonsuza kadar bloke eder.
 */

const { app, ipcMain } = require("electron");
const path = require("node:path");
const fs = require("node:fs");

const FILE = () => path.join(app.getPath("userData"), "ucus-gunlugu.tsv");
const HEADER = "# ucus-gunlugu v1: zaman\trota\tdakika\tkm\tatlandi";

/** Yaklaşık yedi yıllık yoğun kullanım. */
const KEEP_RECORDS = 50000;
const MAX_BYTES = 4 * 1024 * 1024;

function read() {
  try {
    return fs.readFileSync(FILE(), "utf8");
  } catch {
    return "";
  }
}

function sizeOf() {
  try {
    return fs.statSync(FILE()).size;
  } catch {
    return 0;
  }
}

/** Dosya şiştiğinde en yeni kayıtlar tutulur, gerisi atılır. */
function compact() {
  try {
    const lines = read()
      .split("\n")
      .filter((l) => l && !l.startsWith("#"));
    fs.writeFileSync(FILE(), HEADER + "\n" + lines.slice(-KEEP_RECORDS).join("\n") + "\n", "utf8");
  } catch {
    /* sıkıştırma başarısızsa dosya olduğu gibi kalır */
  }
}

/** app.whenReady()'den sonra bir kez çağrılır. */
function registerJournalIpc() {
  ipcMain.handle("journal-read", () => read());

  ipcMain.on("journal-append", (_e, line) => {
    try {
      const size = sizeOf();
      fs.appendFileSync(FILE(), (size === 0 ? HEADER + "\n" : "") + line + "\n", "utf8");
      if (size > MAX_BYTES) compact();
    } catch {
      /* günlük tutulamıyorsa sayaç çalışmaya devam etsin */
    }
  });

  ipcMain.on("journal-clear", () => {
    try {
      fs.rmSync(FILE(), { force: true });
    } catch {
      /* bir sonraki denemede yine denenir */
    }
  });
}

module.exports = { registerJournalIpc, JOURNAL_FILE: FILE, JOURNAL_HEADER: HEADER };
