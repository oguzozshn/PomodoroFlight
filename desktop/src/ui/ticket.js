/**
 * Biniş kartı ekranı. Android sürümündeki ui/TicketScreen.kt karşılığı.
 *
 * Bilet yazıcı yuvasından duraklayarak beslenir, sonra perforasyon boyunca
 * sürüklenerek koparılır: yarıdan azken bırakılırsa geri yapışır, yarıyı
 * geçince kopar ve uçuş başlayabilir. Dokunsal geri bildirim Android'de
 * titreşimle veriliyordu; masaüstünde karşılığı yok, kısa bir tık sesi var.
 */

import { formatNumber, formatTimeOfDay, upperTr } from "../core/format.js";
import { formatDuration } from "../core/estimates.js";

const $ = s => document.querySelector(s);

/** Kâğıdın yuvadan tamamen çıkması. */
const PRINT_MS = 1900;
/** Bu orandan sonra bırakılırsa kopar; altındaysa geri yapışır. */
const TEAR_THRESHOLD = 0.55;
/** Yırtık boyunca kaç tırtıkta ses verilir. */
const TEAR_TICKS = 9;

export function createTicketScreen({ onTorn, onCancel, onTick }) {
  const overlay = $("#ticket");
  const card = $("#ticketCard");
  const tearCanvas = $("#tearLine");
  const tearCtx = tearCanvas.getContext("2d");
  const barcodeCanvas = $("#tkBarcode");
  const barcodeCtx = barcodeCanvas.getContext("2d");

  let ticket = null;
  let feed = 0;          // 0: yuvada, 1: tamamen çıkmış
  let tear = 0;          // 0: bitişik, 1: kopmuş
  let torn = false;
  let drag = null;
  let raf = 0;
  let lastTick = -1;
  let colors = {};

  function readColors() {
    const s = getComputedStyle(document.documentElement);
    colors = {};
    for (const k of ["line", "amber", "muted", "ink", "panel", "space"]) {
      colors[k] = s.getPropertyValue("--" + k).trim();
    }
  }

  /* ---------------- çizim ---------------- */

  function sizeCanvas(canvas, ctx) {
    const r = canvas.getBoundingClientRect();
    if (!r.width) return false;
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    canvas.width = Math.round(r.width * dpr);
    canvas.height = Math.round(r.height * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    return true;
  }

  /**
   * Koparma çizgisi. `tear` kadarlık kısmı tırtıklı bir yırtık olarak, kalanı
   * delikli perforasyon olarak çizilir.
   */
  function drawTear() {
    if (!sizeCanvas(tearCanvas, tearCtx)) return;
    const w = tearCanvas.getBoundingClientRect().width;
    const h = tearCanvas.getBoundingClientRect().height;
    const mid = h / 2;
    const tornX = tear * w;

    tearCtx.clearRect(0, 0, w, h);

    // yırtılmış kısım: doğrusal değil, tırtıklı
    if (tornX > 1) {
      tearCtx.beginPath();
      tearCtx.moveTo(0, mid);
      for (let x = 0; x <= tornX; x += 6) {
        const jag = ((Math.sin(x * 0.7) + Math.sin(x * 0.31)) * 1.6);
        tearCtx.lineTo(x, mid + jag);
      }
      tearCtx.strokeStyle = colors.muted;
      tearCtx.lineWidth = 1.4;
      tearCtx.stroke();
    }

    // henüz koparılmamış kısım: delikli çizgi
    tearCtx.beginPath();
    tearCtx.setLineDash([4, 5]);
    tearCtx.moveTo(tornX, mid);
    tearCtx.lineTo(w, mid);
    tearCtx.strokeStyle = colors.line;
    tearCtx.lineWidth = 1.2;
    tearCtx.stroke();
    tearCtx.setLineDash([]);

    // kenar çentikleri
    tearCtx.fillStyle = colors.space;
    for (const cx of [0, w]) {
      tearCtx.beginPath();
      tearCtx.arc(cx, mid, 5, 0, Math.PI * 2);
      tearCtx.fill();
      tearCtx.beginPath();
      tearCtx.arc(cx, mid, 5, 0, Math.PI * 2);
      tearCtx.strokeStyle = colors.line;
      tearCtx.lineWidth = 1;
      tearCtx.stroke();
    }
  }

  function drawBarcode() {
    if (!ticket || !sizeCanvas(barcodeCanvas, barcodeCtx)) return;
    const w = barcodeCanvas.getBoundingClientRect().width;
    const h = barcodeCanvas.getBoundingClientRect().height;
    barcodeCtx.clearRect(0, 0, w, h);

    const unit = w / ticket.barcode.reduce((a, b) => a + b + 1, 0);
    let x = 0;
    barcodeCtx.fillStyle = colors.ink;
    for (const bar of ticket.barcode) {
      barcodeCtx.fillRect(x, 0, Math.max(1, bar * unit - 0.6), h);
      x += (bar + 1) * unit;
    }
  }

  /** Kâğıdın yuvadan ne kadar çıktığı ve koparılan parçanın sarkması. */
  function applyTransform() {
    const height = card.getBoundingClientRect().height || 1;
    card.style.transform = "translateY(" + ((feed - 1) * height).toFixed(1) + "px)";

    const body = $("#ticketBody");
    body.style.transform =
      "rotate(" + (-4.5 * tear).toFixed(2) + "deg) translateY(" + ((torn ? 26 : 10) * tear).toFixed(1) + "px)";
    body.style.opacity = torn ? String(Math.max(0, 1 - (tear - 1) * 2)) : "1";
  }

  /* ---------------- besleme animasyonu ---------------- */

  function runFeed() {
    const start = performance.now();
    cancelAnimationFrame(raf);
    const step = now => {
      const t = Math.min(1, (now - start) / PRINT_MS);
      // Yazıcı kâğıdı tek hamlede değil, duraklayarak besler.
      const stepped = t < 0.34 ? t * 1.5
        : t < 0.46 ? 0.51
        : t < 0.82 ? 0.51 + (t - 0.46) * 1.16
        : 0.93 + (t - 0.82) * 0.39;
      feed = Math.min(1, stepped);
      applyTransform();
      if (t < 1) {
        raf = requestAnimationFrame(step);
      } else {
        feed = 1;
        applyTransform();
        $("#tearHint").textContent = "Perforasyon boyunca sağa sürükleyerek kopar";
      }
    };
    raf = requestAnimationFrame(step);
  }

  /* ---------------- koparma ---------------- */

  function settle() {
    const from = tear;
    const to = tear >= TEAR_THRESHOLD ? 1 : 0;
    const willTear = to === 1;
    const start = performance.now();
    const ms = willTear ? 200 : 260;

    cancelAnimationFrame(raf);
    const step = now => {
      const t = Math.min(1, (now - start) / ms);
      // geri yapışırken yumuşak, koparken düz
      const e = willTear ? t : 1 - (1 - t) * (1 - t);
      tear = from + (to - from) * e;
      applyTransform();
      drawTear();
      if (t < 1) { raf = requestAnimationFrame(step); return; }

      if (willTear) {
        torn = true;
        applyTransform();
        $("#tearHint").textContent = "Bilet koparıldı — iyi uçuşlar.";
        onTorn?.(ticket);
      } else {
        lastTick = -1;
        $("#tearHint").textContent = "Yarıyı geçmeden bıraktın; bilet geri yapıştı.";
      }
    };
    raf = requestAnimationFrame(step);
  }

  tearCanvas.addEventListener("pointerdown", e => {
    if (torn || feed < 0.995) return;
    try { tearCanvas.setPointerCapture(e.pointerId); } catch { /* sentetik olay */ }
    drag = { x: e.offsetX };
  });

  tearCanvas.addEventListener("pointermove", e => {
    if (!drag) return;
    const width = tearCanvas.getBoundingClientRect().width || 1;
    tear = Math.max(0, Math.min(1, tear + (e.offsetX - drag.x) / width));
    drag.x = e.offsetX;

    const step = Math.floor(tear * TEAR_TICKS);
    if (step !== lastTick) { lastTick = step; onTick?.(); }

    applyTransform();
    drawTear();
  });

  const endDrag = e => {
    if (!drag) return;
    drag = null;
    try { tearCanvas.releasePointerCapture(e.pointerId); } catch { /* sentetik olay */ }
    settle();
  };
  tearCanvas.addEventListener("pointerup", endDrag);
  tearCanvas.addEventListener("pointercancel", endDrag);

  /* ---------------- alanlar ---------------- */

  // Sütun genişlikleri kapsayıcıya CSSOM'dan veriliyor; işaretleme içindeki
  // inline stiller sayfanın CSP'sine takılır ve uygulanmaz.
  const field = (label, value) =>
    '<div class="f"><div class="k">' + upperTr(label) + "</div>" +
    '<div class="v">' + value + "</div></div>";

  function renderFields() {
    const t = ticket;
    $("#tkOCode").textContent = t.route.o;
    $("#tkOCity").textContent = t.route.oName;
    $("#tkDCode").textContent = t.route.d;
    $("#tkDCity").textContent = t.route.dName;

    const rows = [
      [$("#tkRow1"), "2.2fr 1.2fr 1fr 1fr", [
        ["Yolcu", t.passenger], ["Uçuş", t.flightNo], ["Kapı", t.gate], ["Koltuk", t.seat]
      ]],
      [$("#tkRow2"), "1fr 1fr 1.4fr .9fr", [
        ["Biniş", formatTimeOfDay(t.boardingAt)],
        ["Kalkış", formatTimeOfDay(t.departureAt)],
        ["Varış", formatTimeOfDay(t.arrivalAt, t.boardingAt)],
        ["Sınıf", "ODAK"]
      ]],
      [$("#tkRow3"), "1.3fr 1.3fr 1fr", [
        ["Mesafe", formatNumber(t.distanceKm) + " km"],
        ["Bacak", t.legs + " pomodoro"],
        ["Odak", formatDuration(t.focusMinutes)]
      ]]
    ];

    for (const [box, cols, items] of rows) {
      box.style.gridTemplateColumns = cols;
      box.innerHTML = items.map(([k, v]) => field(k, v)).join("");
    }
  }

  /* ---------------- dış yüzey ---------------- */

  function open(issued) {
    ticket = issued;
    feed = 0; tear = 0; torn = false; drag = null; lastTick = -1;

    readColors();
    renderFields();
    overlay.hidden = false;
    // Ölçüler ancak görünür olunca doğru; çizim bir kare sonra.
    requestAnimationFrame(() => {
      applyTransform();
      drawTear();
      drawBarcode();
      $("#tearHint").textContent = "Bilet yazdırılıyor…";
      runFeed();
    });
  }

  function close() {
    if (overlay.hidden) return;
    overlay.hidden = true;
    cancelAnimationFrame(raf);
    raf = 0;
  }

  $("#ticketCancel").addEventListener("click", () => { close(); onCancel?.(); });
  overlay.addEventListener("click", e => { if (e.target === overlay) { close(); onCancel?.(); } });

  return { open, close, isOpen: () => !overlay.hidden, get torn() { return torn; } };
}
