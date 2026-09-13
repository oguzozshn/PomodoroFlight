/**
 * Mil bildirimi. Android sürümündeki ui/LogbookScreen.kt karşılığı.
 *
 * Kasıtlı olarak diğer pomodoro uygulamalarının çubuk grafiğine benzemiyor:
 * bu uygulamada dakikalar zaten mesafeye ve rotaya çevrildiği için havayolu
 * bildirimi daha doğru bir çerçeve.
 */

import { registry } from "../core/places.js";
import { buildRoute, samplePath } from "../core/route.js";
import { worldLaps, currentLapFraction, formatDuration } from "../core/estimates.js";
import { tierOf, nextTierOf, tierProgress, kmToNextTier } from "../core/card-tier.js";
import { formatNumber, formatDate, upperTr } from "../core/format.js";
import {
  dailyCounts, streak, hourHistogram, peakWindow, topRoutes,
  busiestDay, activeDays, focusMinutes, airports, firstRecordTime
} from "../core/journal-stats.js";
import { createGlobe } from "./globe.js";

const $ = s => document.querySelector(s);
/** Pomodoro sayısı artık kesirli olabilir (37 dk = 1,48); bir ondalık yeter. */
const pom = n => n.toLocaleString("tr-TR", { maximumFractionDigits: 1 });
const CAL_WEEKS = 12;

const MONTHS = ["Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
  "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"];

/** Isı takvimindeki kutu yoğunluğu, 0–4. */
function level(count, max) {
  if (count <= 0) return 0;
  if (max <= 1) return 4;
  return Math.max(1, Math.min(4, Math.ceil((count / max) * 4)));
}

export function createStatement({ getRecords, getData, getWorld }) {
  const overlay = $("#statement");
  const map = createGlobe($("#stMap"));
  let mapLoop = 0;

  function renderCard(db, km) {
    const tier = tierOf(km), next = nextTierOf(km);
    $("#stPax").textContent = upperTr(db.passenger?.trim() || "Yolcu");
    $("#stTier").textContent = tier.title;
    $("#stMiles").innerHTML = formatNumber(km) + "<small>km</small>";
    $("#stTierBar").style.width = (tierProgress(km) * 100).toFixed(1) + "%";
    $("#stTierNote").textContent = next
      ? formatNumber(kmToNextTier(km)) + " km sonra " + next.title
      : "En üst seviyedesin — buradan sonrası saf keyif.";
  }

  function renderLaps(km) {
    const completed = Math.floor(worldLaps(km));
    $("#stLaps").textContent = completed > 0 ? completed + " tur tamam" : "ilk tur sürüyor";
    $("#stLapBar").style.width = (currentLapFraction(km) * 100).toFixed(1) + "%";
  }

  function renderFigures(db, records) {
    const s = streak(records);
    const port = airports(records).size;
    const busiest = busiestDay(records);
    const figures = [
      ["Seri", s > 0 ? s + " gün" : "—"],
      ["Aktif gün", String(activeDays(records))],
      ["Kayıtlı odak", formatDuration(focusMinutes(records))],
      ["Tamamlanan uçuş", String(db.log.length)],
      ["Uğranan yer", port > 0 ? String(port) : "—"],
      ["En yoğun gün", busiest ? pom(busiest.count) + " pom" : "—"]
    ];
    $("#stFigures").innerHTML = figures
      .map(([k, v]) => '<div><div class="k">' + k + '</div><div class="v">' + v + "</div></div>")
      .join("");
  }

  function renderCalendar(records) {
    const days = dailyCounts(records, CAL_WEEKS * 7);
    const max = days.reduce((m, d) => Math.max(m, d.count), 0);
    const box = $("#stCal");
    box.innerHTML = "";

    for (const day of days) {
      const cell = document.createElement("i");
      cell.dataset.level = String(level(day.count, max));
      cell.dataset.time = String(day.time);
      cell.dataset.count = pom(day.count);
      cell.title = formatDate(day.time) + " · " + pom(day.count) + " pomodoro";
      box.appendChild(cell);
    }

    box.onclick = e => {
      const cell = e.target.closest("i");
      if (!cell) return;
      for (const other of box.children) other.removeAttribute("aria-current");
      cell.setAttribute("aria-current", "true");
      $("#stCalPick").textContent =
        formatDate(Number(cell.dataset.time)) + " · " + cell.dataset.count + " pomodoro";
    };
    $("#stCalPick").textContent = "Bir güne tıkla";
  }

  function renderHours(records) {
    const hours = hourHistogram(records);
    const peak = peakWindow(hours);
    const max = Math.max(1, ...hours);
    const box = $("#stHours");
    box.innerHTML = "";

    // Yükseklik style özniteliğiyle değil CSSOM'dan veriliyor: işaretleme
    // içindeki inline stiller sayfanın CSP'sine takılıyor ve uygulanmıyor.
    hours.forEach((n, h) => {
      const bar = document.createElement("i");
      bar.style.height = Math.round((n / max) * 100) + "%";
      bar.dataset.peak = String(peak !== null && (h === peak || h === (peak + 1) % 24));
      bar.title = String(h).padStart(2, "0") + ":00 · " + pom(n) + " pomodoro";
      box.appendChild(bar);
    });

    $("#stPeak").textContent = peak === null
      ? ""
      : "en verimli " + String(peak).padStart(2, "0") + ":00–" +
        String((peak + 2) % 24).padStart(2, "0") + ":00";
  }

  function renderTop(records) {
    const rows = topRoutes(records, 5);
    $("#stTop").innerHTML = rows.length
      ? rows.map(r => {
          const [o, d] = r.routeKey.split("-");
          return '<div class="hat"><span class="pair">' + o + " → " + d + "</span>" +
            "<span>" + (registry.find(d)?.city ?? d) + "</span>" +
            '<span class="n">' + pom(r.count) + " pom</span></div>";
        }).join("")
      : '<p class="note">Kayıt başlayınca en çok uçtuğun hat burada görünecek.</p>';
  }

  function renderLogs(db) {
    const ul = $("#stLogs");
    ul.innerHTML = "";
    $("#stLogsEmpty").style.display = db.log.length ? "none" : "block";
    for (const e of db.log) {
      const [o, d] = e.r.split("-");
      const li = document.createElement("li");
      li.innerHTML = '<span class="st">' + o + "→" + d + "</span>" +
        "<span>" + (registry.find(d)?.city ?? d) + "</span>" +
        '<span class="dt">' + formatDate(e.t) + "</span>";
      ul.appendChild(li);
    }
  }

  function renderSince(records) {
    const first = firstRecordTime(records);
    $("#stSince").textContent = first === null
      ? "Burası ilk pomodoron bittiğinde dolmaya başlar — bozuk değil, henüz boş."
      : (() => {
          const d = new Date(first);
          return "Gün bazlı kayıt " + d.getDate() + " " + MONTHS[d.getMonth()] + " " +
            d.getFullYear() + " tarihinde başladı; toplam mil ve uçuş günlüğü baştan beri tutuluyor.";
        })();
  }

  /** Uçulan bütün rotaların izi; küre yavaşça döner. */
  function startMap(db) {
    const trails = [];
    const seen = new Set();
    for (const entry of db.log) {
      if (seen.has(entry.r)) continue;
      seen.add(entry.r);
      const r = buildRoute(entry.r);
      if (r) trails.push(samplePath(r, 96));
    }

    map.readTokens();
    map.resize();
    const first = db.log.length ? buildRoute(db.log[0].r) : null;
    map.aim(first ? first.center[0] : 20, first ? first.center[1] : 20, true);

    cancelAnimationFrame(mapLoop);
    const spin = () => {
      map.rotateBy(0, 0.08);           // yavaş dönüş; harita durağan durmasın
      map.draw({ world: getWorld(), trails, places: [] });
      mapLoop = requestAnimationFrame(spin);
    };
    spin();
  }

  function open() {
    const db = getData();
    const records = getRecords();
    renderCard(db, db.totalKm);
    renderLaps(db.totalKm);
    renderFigures(db, records);
    renderCalendar(records);
    renderHours(records);
    renderTop(records);
    renderLogs(db);
    renderSince(records);
    overlay.hidden = false;
    startMap(db);
    $("#statementClose").focus();
  }

  function close() {
    overlay.hidden = true;
    cancelAnimationFrame(mapLoop);
    mapLoop = 0;
  }

  $("#statementBtn").addEventListener("click", open);
  $("#statementClose").addEventListener("click", close);
  overlay.addEventListener("click", e => { if (e.target === overlay) close(); });

  return { open, close, isOpen: () => !overlay.hidden };
}
