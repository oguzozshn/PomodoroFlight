"use strict";

/**
 * src/ klasörünü `app://` şeması altından servis eder.
 *
 * Neden gerekli: ES modülleri `file://` üzerinden yüklenmez — Chromium modül
 * isteklerini CORS ile yapar, file:// kaynağı ise opak sayılır ve istek sessizce
 * düşer. Standart bir şema tanımlayınca sayfa gerçek bir origin kazanır,
 * modüller ve CSP'deki 'self' beklendiği gibi çalışır.
 *
 * Mobil ve web tarafında böyle bir katman gerekmiyor: Capacitor da statik
 * sunucu da zaten http(s) üzerinden servis ediyor.
 */

const { protocol } = require("electron");
const path = require("node:path");
const fs = require("node:fs");

const SCHEME = "app";
const HOST = "ucus";

const MIME = {
  ".html": "text/html; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".woff2": "font/woff2",
  ".png": "image/png",
  ".svg": "image/svg+xml"
};

/** app.whenReady()'den ÖNCE, modül yüklenirken çağrılmalı. */
function registerScheme() {
  protocol.registerSchemesAsPrivileged([
    {
      scheme: SCHEME,
      privileges: { standard: true, secure: true, supportFetchAPI: true, stream: true }
    }
  ]);
}

/**
 * Pencere görüntüsü olarak seçilen video. Dosya uygulamanın içine kopyalanmaz;
 * yalnızca yolu tutulur ve `app://ucus/__media` altından okunur. Böylece depoda
 * hiçbir telifli görüntü durmaz ve sayfanın CSP'si `media-src 'self'` kalır.
 */
let userMedia = null;
const setUserMedia = (file) => { userMedia = file || null; };

const MEDIA_MIME = {
  ".mp4": "video/mp4", ".m4v": "video/mp4", ".webm": "video/webm",
  ".mov": "video/quicktime", ".mkv": "video/x-matroska",
};

/** app.whenReady()'den SONRA çağrılır. */
function serve(rootDir) {
  const root = path.resolve(rootDir);

  protocol.handle(SCHEME, async request => {
    let rel;
    try {
      rel = decodeURIComponent(new URL(request.url).pathname);
    } catch {
      return new Response("bozuk istek", { status: 400 });
    }

    // Kullanıcının seçtiği video: kök dışında, ayrı bir yol.
    if (rel === "/__media") {
      if (!userMedia) return new Response("bulunamadı", { status: 404 });
      try {
        const body = await fs.promises.readFile(userMedia);
        return new Response(body, {
          headers: {
            "content-type": MEDIA_MIME[path.extname(userMedia).toLowerCase()] || "video/mp4",
          },
        });
      } catch {
        return new Response("okunamadı", { status: 404 });
      }
    }

    const file = path.normalize(path.join(root, rel === "/" ? "/index.html" : rel));
    // Kök dışına çıkan yolları reddet (../ ile kaçış).
    if (file !== root && !file.startsWith(root + path.sep)) {
      return new Response("izin yok", { status: 403 });
    }

    try {
      const body = await fs.promises.readFile(file);
      return new Response(body, {
        headers: { "content-type": MIME[path.extname(file).toLowerCase()] || "application/octet-stream" }
      });
    } catch {
      return new Response("bulunamadı", { status: 404 });
    }
  });
}

module.exports = { SCHEME, HOST, registerScheme, serve, setUserMedia, START_URL: `${SCHEME}://${HOST}/index.html` };
