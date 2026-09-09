/**
 * Rota kurma: kalkış/varış yuvaları, arama ve biniş kartı önizlemesi.
 * Android sürümündeki ui/RouteScreen.kt karşılığı.
 *
 * Açılır menü yerine arama var: veri setinde 3.941 havalimanı bulunuyor,
 * hepsini bir `select` içine koymak kullanılabilir değil.
 */

import { registry, countryName } from "../core/places.js";
import { buildRoute, isValidPair } from "../core/route.js";
import {
  gateOf, remainingScheduleMinutes, focusMinutesFor, realFlightMinutes, formatDuration
} from "../core/estimates.js";
import { formatNumber, formatTimeOfDay } from "../core/format.js";
import { flightState } from "../core/state.js";

const $ = s => document.querySelector(s);
const MAX_RESULTS = 24;

export function createRoutePicker({ getData, getRoute, onLoad, onIssue }) {
  const input = $("#search");
  const list = $("#results");
  let slot = "from";          // hangi yuva doldurulacak
  let from = null, to = null; // seçili kodlar
  let marked = -1;            // klavyeyle gezinilen sonuç

  /* ---------------- yuvalar ---------------- */

  function renderSlots() {
    for (const [id, code] of [["From", from], ["To", to]]) {
      const place = code ? registry.find(code) : null;
      $("#slot" + id + "City").textContent = place ? place.city : "seç";
      $("#slot" + id + "Meta").textContent = place
        ? place.code + " · " + (countryName(place.country) || "—")
        : "boş";
    }
    $("#slotFrom").setAttribute("aria-pressed", String(slot === "from"));
    $("#slotTo").setAttribute("aria-pressed", String(slot === "to"));
    $("#searchLabel").textContent = slot === "from" ? "Kalkış şehri ara" : "Varış şehri ara";
  }

  function focusSlot(which) {
    slot = which;
    renderSlots();
    input.value = "";
    renderResults([]);
    input.focus();
  }

  /* ---------------- arama ---------------- */

  function renderResults(places) {
    list.innerHTML = "";
    marked = -1;
    if (!places.length) return;

    // Aynı şehirde birden çok havalimanı varsa (Tokyo: NRT ve HND) yalnızca
    // şehir adı ayırt etmiyor; o satırlarda havalimanının kendi adı da yazılır.
    const seen = new Map();
    for (const p of places) seen.set(p.city, (seen.get(p.city) ?? 0) + 1);

    for (const place of places) {
      const li = document.createElement("li");
      li.setAttribute("role", "option");
      li.dataset.code = place.code;
      const ambiguous = seen.get(place.city) > 1 && place.airportName &&
        place.airportName !== place.city;
      li.innerHTML =
        '<span class="code">' + place.code + "</span>" +
        '<span class="name">' + place.city +
        (ambiguous ? ' <span class="alt">· ' + place.airportName + "</span>" : "") + "</span>" +
        '<span class="ctry">' + (countryName(place.country) || "") + "</span>";
      list.appendChild(li);
    }
  }

  function search() {
    const q = input.value.trim();
    if (!q) { renderResults([]); return; }
    const hits = registry.search(q, MAX_RESULTS);
    if (!hits.length) {
      list.innerHTML = '<li class="none">Sonuç yok.</li>';
      marked = -1;
      return;
    }
    renderResults(hits);
  }

  function choose(code) {
    if (!code) return;
    if (slot === "from") {
      from = code;
      if (to === code) to = null;
      slot = "to";
    } else {
      if (from === code) {
        $("#msg").textContent = "Kalkış ve varış aynı olamaz — başka bir varış seç.";
        return;
      }
      to = code;
    }
    input.value = "";
    renderResults([]);
    renderSlots();
    tryLoad();
    input.focus();
  }

  function tryLoad() {
    if (!from || !to) { renderPreview(); return; }
    if (!isValidPair(from, to)) {
      $("#msg").textContent = "Bu rota kurulamadı.";
      return;
    }
    onLoad(from + "-" + to);
    renderPreview();
  }

  /* ---------------- biniş kartı önizlemesi ---------------- */

  function renderPreview() {
    const route = getRoute();
    const cells = $("#pvCells");
    const summary = $("#pvSummary");
    if (!route) { cells.innerHTML = ""; summary.innerHTML = ""; return; }

    const db = getData();
    const state = flightState(db, route, { phase: "focus", elapsed: () => 0 });
    const schedule = remainingScheduleMinutes(state.legsLeft, db.focusMin, db.breakMin, db.cycle);
    const now = Date.now();
    const arrival = now + schedule * 60000;

    const cell = (k, v) => '<div><div class="k">' + k + '</div><div class="v">' + v + "</div></div>";
    cells.innerHTML =
      cell("Uçuş", route.flightNo) + cell("Kapı", gateOf(route.key)) +
      cell("Bacak", state.done + "/" + route.legs) +
      cell("Kalkış", formatTimeOfDay(now)) +
      cell("Varış", formatTimeOfDay(arrival, now)) +
      cell("Süre", formatDuration(schedule));

    const row = (k, v) => "<div><dt>" + k + "</dt><dd>" + v + "</dd></div>";
    summary.innerHTML =
      row("Mesafe", formatNumber(route.km) + " km") +
      row("Odakta geçecek", formatDuration(focusMinutesFor(state.legsLeft, db.focusMin))) +
      row("Gerçek uçuş süresi", "≈ " + formatDuration(realFlightMinutes(route.km)));

    const btn = $("#issueBtn");
    btn.disabled = false;
    btn.textContent = state.arrived ? "Tekrar uç"
      : state.started ? "Uçuşa devam et" : "Bilet kes";
  }

  /* ---------------- olaylar ---------------- */

  $("#slotFrom").addEventListener("click", () => focusSlot("from"));
  $("#slotTo").addEventListener("click", () => focusSlot("to"));
  input.addEventListener("input", search);

  list.addEventListener("click", e => {
    const li = e.target.closest("li[data-code]");
    if (li) choose(li.dataset.code);
  });

  input.addEventListener("keydown", e => {
    const items = [...list.querySelectorAll("li[data-code]")];
    if (e.key === "ArrowDown" || e.key === "ArrowUp") {
      if (!items.length) return;
      e.preventDefault();
      marked = (marked + (e.key === "ArrowDown" ? 1 : -1) + items.length) % items.length;
      items.forEach((li, i) => li.setAttribute("aria-selected", String(i === marked)));
      items[marked].scrollIntoView({ block: "nearest" });
    } else if (e.key === "Enter") {
      e.preventDefault();
      choose(items[marked >= 0 ? marked : 0]?.dataset.code);
    } else if (e.key === "Escape" && input.value) {
      e.stopPropagation();
      input.value = "";
      renderResults([]);
    }
  });

  $("#issueBtn").addEventListener("click", () => onIssue());

  /** Rota dışarıdan değişince (küreden seçim, hazır rota) yuvaları eşitle. */
  function sync() {
    const route = getRoute();
    if (route) { from = route.o; to = route.d; }
    renderSlots();
    renderPreview();
  }

  return { sync, focusSlot };
}
