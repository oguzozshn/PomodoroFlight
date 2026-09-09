"use strict";

/**
 * Pencere görüntüsü olarak kullanılacak video.
 *
 * Dosya uygulamanın içine kopyalanmaz, paketine de girmez: yalnızca seçilen
 * yolun kendisi tutulur ve `app://ucus/__media` altından okunur. Böylece depoda
 * hiçbir telifli görüntü durmaz ve sayfanın CSP'si `media-src 'self'` kalabilir.
 */

const { ipcMain, dialog } = require("electron");
const { setUserMedia } = require("./serve.js");

/** app.whenReady()'den sonra bir kez çağrılır. */
function registerMediaIpc(getWindow) {
  ipcMain.handle("pick-video", async () => {
    const parent = typeof getWindow === "function" ? getWindow() : null;
    const options = {
      title: "Pencere görüntüsü seç",
      properties: ["openFile"],
      filters: [{ name: "Video", extensions: ["mp4", "m4v", "webm", "mov", "mkv"] }],
    };
    const result = parent && !parent.isDestroyed()
      ? await dialog.showOpenDialog(parent, options)
      : await dialog.showOpenDialog(options);

    if (result.canceled || !result.filePaths.length) return null;
    setUserMedia(result.filePaths[0]);
    // Sorgu eki tarayıcının eski dosyayı önbellekten vermesini engeller.
    return "__media?t=" + Date.now();
  });
}

module.exports = { registerMediaIpc };
