/**
 * Pencereden bakış ekranı. Android sürümündeki ui/window/WindowScreen.kt.
 *
 * Tam ekran açılır, ekran uyanık kalır, bir süre kıpırdanmayınca göstergeler
 * çekilir; geriye yalnızca pencere ve kalan süre kalır. Üç kaynak arasında
 * gidip gelinir ve seçim cihazda saklanır:
 *
 *   gerçek zamanlı — rotanın o anki noktasından, gerçek saate göre gökyüzü
 *   küre           — giriş ekranındaki kürenin uçağı izleyen hâli
 *   video          — kendi seçtiğin pencere görüntüsü, sessiz ve döngüde
 */

import { formatClock } from "../../core/timer.js";
import { createGlobe } from "../globe.js";
import { createRealtimeWindow } from "./realtime.js";
import { drawWing, DEFAULT_WING } from "./wing.js";
import { qualityOf, DEFAULT_QUALITY } from "./quality.js";

const $ = s => document.querySelector(s);

/** Göstergeler bu kadar hareketsizlikten sonra çekilir. */
const IDLE_MS = 3200;

/** Kabin penceresinin ölçüleri: gerçeğinde olduğu gibi enden uzun. */
const WINDOW_HEIGHT_RATIO = 0.82;
const WINDOW_ASPECT = 0.72;          // genişlik / yükseklik

export function createWindowView({ getScene, getData, onToggle, onSkip, onModeChange, onPickVideo }) {
  const root = $("#windowView");
  const canvas = $("#windowCanvas");
  const ctx = canvas.getContext("2d");
  const video = $("#windowVideo");

  const globe = createGlobe(canvas);

  // Kalite değişince sahne yeniden kurulur: bulut ve yıldız alanları örnek
  // sayısına bağlı olarak bir kez üretiliyor.
  let qualityKey = DEFAULT_QUALITY;
  let realtime = createRealtimeWindow(qualityKey);

  function applyQuality(next) {
    if (next === qualityKey) return;
    qualityKey = next;
    realtime = createRealtimeWindow(qualityKey);
    resize();
  }

  /** Bulanık zemin için videonun küçültülmüş kopyası. */
  const blurBuf = document.createElement("canvas");
  blurBuf.width = 96; blurBuf.height = 54;
  const blurCtx = blurBuf.getContext("2d");

  let mode = "realtime";
  /** Küre kipinde kamera uçağı izliyor mu; sürükleyince serbest kalır. */
  let globeLocked = true;
  let drag = null;
  let raf = 0;
  let idleTimer = 0;
  let started = 0;
  let W = 0, H = 0;

  function resize() {
    const r = root.getBoundingClientRect();
    if (!r.width) return;
    // Süper örnekleme: sahne ekrandan büyük çizilip küçültülerek gösterilir,
    // kenarlar ve ince çizgiler yumuşar.
    const superSample = qualityOf(qualityKey).scale;
    const dpr = Math.min(window.devicePixelRatio || 1, 2) * superSample;
    W = r.width; H = r.height;
    canvas.width = Math.round(W * dpr);
    canvas.height = Math.round(H * dpr);
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    globe.resize(superSample);
  }

  /** Pencere deliğinin ekrandaki yeri. */
  function windowRect() {
    const h = H * WINDOW_HEIGHT_RATIO;
    const w = Math.min(h * WINDOW_ASPECT, W * 0.92);
    return { x: (W - w) / 2, y: (H - h) / 2, w, h, r: Math.min(w, h) * 0.34 };
  }

  function traceWindow(rect) {
    const { x, y, w, h, r } = rect;
    ctx.moveTo(x + r, y);
    ctx.arcTo(x + w, y, x + w, y + h, r);
    ctx.arcTo(x + w, y + h, x, y + h, r);
    ctx.arcTo(x, y + h, x, y, r);
    ctx.arcTo(x, y, x + w, y, r);
    ctx.closePath();
  }

  /**
   * Kabin çerçevesi: pencerenin dışında kalan her şey kabin duvarıyla örtülür.
   * Uçağa bağlı olduğu için dünya çalkalanırken sabit durur.
   */
  function drawCabin(rect) {
    ctx.save();
    ctx.beginPath();
    ctx.rect(0, 0, W, H);            // dış dikdörtgen
    traceWindow(rect);               // iç pencere: even-odd delik açar
    const wall = ctx.createLinearGradient(0, 0, 0, H);
    wall.addColorStop(0, "#161b23");
    wall.addColorStop(0.55, "#0e1219");
    wall.addColorStop(1, "#080b11");
    ctx.fillStyle = wall;
    ctx.fill("evenodd");

    // çerçevenin iç kenarındaki ince ışık
    ctx.beginPath();
    traceWindow(rect);
    ctx.strokeStyle = "rgba(255,255,255,.13)";
    ctx.lineWidth = 2;
    ctx.stroke();

    // gölge: pencere biraz içeride dursun
    ctx.beginPath();
    traceWindow({ ...rect, x: rect.x - 7, y: rect.y - 7, w: rect.w + 14, h: rect.h + 14, r: rect.r + 7 });
    ctx.strokeStyle = "rgba(0,0,0,.55)";
    ctx.lineWidth = 12;
    ctx.stroke();
    ctx.restore();
  }

  /**
   * Video kipi: tam ekran, kabin çerçevesi yok.
   *
   * Kullanıcının kendi çekiminde zaten pencere ve kanat var; onu bir kez daha
   * çerçevelemek çift çerçeve oluyordu. Görüntü kırpılmadan sığdırılır, artan
   * kenarlar da aynı karenin bulanık ve büyütülmüş kopyasıyla doldurulur —
   * siyah bantlar yerine görüntünün kendi rengi kalır.
   */
  function drawVideo() {
    const vw = video.videoWidth, vh = video.videoHeight;
    if (!vw || !vh) {
      ctx.fillStyle = "#05080e";
      ctx.fillRect(0, 0, W, H);
      return;
    }

    // zemin: küçük bir kopya büyütülüp bulanıklaştırılır (ucuz)
    try {
      blurCtx.drawImage(video, 0, 0, blurBuf.width, blurBuf.height);
      ctx.save();
      ctx.filter = "blur(26px)";
      const cover = Math.max(W / blurBuf.width, H / blurBuf.height);
      const bw = blurBuf.width * cover, bh = blurBuf.height * cover;
      ctx.drawImage(blurBuf, (W - bw) / 2, (H - bh) / 2, bw, bh);
      ctx.restore();
      ctx.fillStyle = "rgba(5,8,14,.45)";
      ctx.fillRect(0, 0, W, H);
    } catch {
      ctx.fillStyle = "#05080e";
      ctx.fillRect(0, 0, W, H);
    }

    // görüntünün kendisi: tamamı görünecek biçimde sığdırılır
    const fit = Math.min(W / vw, H / vh);
    const dw = vw * fit, dh = vh * fit;
    ctx.drawImage(video, (W - dw) / 2, (H - dh) / 2, dw, dh);
  }

  function frame(now) {
    raf = requestAnimationFrame(frame);
    if (!W) return;

    const scene = getScene();
    const time = (now - started) / 1000;

    const rect = windowRect();

    if (mode === "video") {
      drawVideo();
    } else if (mode === "globe") {
      // Küre bütün tuvale çiziliyor; kabin deliğine kırpılırsa yalnızca dar bir
      // dilimi görünüyor ve küreye benzemiyordu. Bu kipte çerçeve çizilmez.
      // Kilitliyken kamera uçağı izler; sürüklenince kullanıcı devralır.
      if (globeLocked) globe.aim(scene.cameraLat, scene.cameraLon, false);
      globe.ease(1 / 60);
      globe.draw({ route: scene.route, fraction: scene.fraction, world: scene.world, places: [] });
    } else {
      const seat = getData().seatSide === "right" ? "right" : "left";
      const info = realtime.draw(ctx, W, H, {
        route: scene.route,
        fraction: scene.fraction,
        altitudeM: scene.altitudeM,
        world: scene.world,
        places: scene.places,
        time,
        seat
      });
      drawWing(ctx, rect, info?.palette, getData().wingModel || DEFAULT_WING, seat);
      drawCabin(rect);
    }

    $("#winClock").textContent = formatClock(scene.remaining);
    $("#winSub").textContent = scene.phaseLabel;
    $("#winGo").textContent = scene.running ? "Beklet" : "Devam";
  }

  /* ---------------- göstergelerin çekilmesi ---------------- */

  function wake() {
    root.dataset.idle = "false";
    clearTimeout(idleTimer);
    idleTimer = setTimeout(() => { root.dataset.idle = "true"; }, IDLE_MS);
  }

  /* ---------------- küre kipinde döndürme ---------------- */

  function setLocked(next) {
    globeLocked = next;
    $("#winRelock").hidden = next || mode !== "globe";
  }

  /** Kamerayı uçağa geri kilitler. */
  function relock() {
    setLocked(true);
    globe.resetView();
    const scene = getScene();
    globe.aim(scene.cameraLat, scene.cameraLon, false);
    wake();
  }

  canvas.addEventListener("pointerdown", e => {
    if (mode !== "globe" || e.button !== 0) return;
    try { canvas.setPointerCapture(e.pointerId); } catch { /* sentetik olay */ }
    globe.stopMomentum();
    drag = { x: e.offsetX, y: e.offsetY, moved: false };
  });

  canvas.addEventListener("pointermove", e => {
    if (!drag || mode !== "globe") return;
    const dx = e.offsetX - drag.x, dy = e.offsetY - drag.y;
    if (!drag.moved && dx * dx + dy * dy > 16) drag.moved = true;
    if (drag.moved) {
      setLocked(false);
      globe.rotateByPixels(dx, dy);
      drag.x = e.offsetX; drag.y = e.offsetY;
    }
  });

  const endGlobeDrag = e => {
    if (!drag) return;
    const moved = drag.moved;
    drag = null;
    try { canvas.releasePointerCapture(e.pointerId); } catch { /* sentetik olay */ }
    if (moved) globe.releaseMomentum();
  };
  canvas.addEventListener("pointerup", endGlobeDrag);
  canvas.addEventListener("pointercancel", endGlobeDrag);

  canvas.addEventListener("wheel", e => {
    if (mode !== "globe") return;
    e.preventDefault();
    setLocked(false);
    globe.zoomAnchored(e.offsetX, e.offsetY, e.deltaY < 0 ? 1.12 : 1 / 1.12);
  }, { passive: false });

  canvas.addEventListener("dblclick", () => { if (mode === "globe") relock(); });
  $("#winRelock").addEventListener("click", relock);

  root.addEventListener("pointermove", wake);
  root.addEventListener("pointerdown", wake);
  root.addEventListener("wheel", wake, { passive: true });
  // Video kipinde imleç <video> üzerinde duruyor; olay yine köke çıkıyor ama
  // klavyeyle de uyandırılabilsin diye belge düzeyinde de dinleniyor.
  document.addEventListener("keydown", () => { if (!root.hidden) wake(); });

  /* ---------------- kipler ---------------- */

  async function setMode(next) {
    mode = next;
    for (const b of $("#winModes").querySelectorAll("button")) {
      b.setAttribute("aria-pressed", String(b.dataset.mode === next));
    }
    video.hidden = next !== "video";
    $("#winNote").textContent = "";
    canvas.style.cursor = next === "globe" ? "grab" : "";
    if (next === "globe") {
      globe.readTokens();                       // tema değişmiş olabilir
      setLocked(true);
      $("#winNote").textContent = "Sürükleyerek çevir · tekerlekle yakınlaştır · çift tıkla uçağa dön";
    } else {
      setLocked(globeLocked);                   // düğme öteki kiplerde gizlenir
    }

    if (next === "video") {
      if (!video.src) {
        const url = await onPickVideo?.();
        if (!url) {
          $("#winNote").textContent =
            "Video seçilmedi. Kendi çekimini kullanabilirsin — dikey, 15–40 saniye, kamerası sabit bir görüntü en iyisi.";
          return;
        }
        video.src = url;
      }
      video.play().catch(() => {
        $("#winNote").textContent = "Video oynatılamadı; dosya biçimi desteklenmiyor olabilir.";
      });
    } else {
      video.pause();
    }
    onModeChange?.(next);
  }

  $("#winModes").addEventListener("click", e => {
    const b = e.target.closest("button");
    if (b) setMode(b.dataset.mode);
  });
  $("#winGo").addEventListener("click", () => { onToggle(); wake(); });
  $("#winSkip").addEventListener("click", () => { onSkip(); wake(); });
  $("#winClose").addEventListener("click", () => close());

  /* ---------------- açılış / kapanış ---------------- */

  function open() {
    root.hidden = false;
    started = performance.now();
    applyQuality(getData().renderQuality || DEFAULT_QUALITY);
    resize();
    globe.readTokens();
    const scene = getScene();
    setLocked(true);
    globe.resetView();
    globe.aim(scene.cameraLat, scene.cameraLon, true);
    wake();
    cancelAnimationFrame(raf);
    raf = requestAnimationFrame(frame);
    setMode(getData().windowMode || "realtime");
  }

  function close() {
    if (root.hidden) return;
    root.hidden = true;
    cancelAnimationFrame(raf);
    raf = 0;
    clearTimeout(idleTimer);
    video.pause();
  }

  const ro = new ResizeObserver(() => { if (!root.hidden) resize(); });
  ro.observe(root);

  return { open, close, isOpen: () => !root.hidden, setMode, applyQuality };
}
