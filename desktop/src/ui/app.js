/**
 * Arayüz katmanı: DOM bağlama, çizim döngüsü ve etkileşim.
 * Alan mantığı core/ altında, işletim sistemi köprüsü platform/ altında —
 * burada yalnızca ikisini ekrana bağlayan kod var.
 */

import { PRESETS } from "../core/airports.js";
import { registry, ensurePlacesLoaded, countryName } from "../core/places.js";
import {
  buildRoute, kmBetween, legsFor, kmForFocus, isValidPair, samplePath, pointAt
} from "../core/route.js";
import { createTimer, formatClock } from "../core/timer.js";
import { POMODORO_MIN } from "../core/geo.js";
import { createStore } from "../core/store.js";
import { flightState, durationMsOf } from "../core/state.js";
import { createWorld } from "../core/shapes.js";
import { createJournal, focusRecord } from "../core/journal.js";
import { journalStorage } from "../platform/journal-storage.js";
import { subsolarPoint } from "../core/sun.js";
import { platform, syncPlatform } from "../platform/index.js";
import { loadBinary } from "../platform/assets.js";
import { chime } from "../platform/chime.js";
import { createGlobe } from "./globe.js";
import { createStatement } from "./logbook.js";
import { createTicketScreen } from "./ticket.js";
import { createRoutePicker } from "./route-picker.js";
import { createWindowView } from "./window/index.js";
import { issueTicket } from "../core/ticket.js";

const $ = s => document.querySelector(s);
const nf = n => Math.round(n).toLocaleString("tr-TR");
/** Pomodoro sayısı: tam sayıysa ondalıksız, değilse "1,48". */
const fmtPom = n => n.toLocaleString("tr-TR", { maximumFractionDigits: 2 });
/** Kodun gösterilecek şehir adı; bilinmeyen kodda kodun kendisi. */
const cityOf = code => registry.find(code)?.city ?? code;

const store = createStore();
const db = store.data;
const save = () => store.save();

const globe = createGlobe($("#map"));
const world = createWorld(loadBinary);
const journal = createJournal(journalStorage);
let records = [];                 // gün bazlı istatistiklerin kaynağı; loadData() doldurur

let statement = null;   // mil bildirimi penceresi; boot() içinde kurulur
let ticketScreen = null;
let picker = null;
let windowView = null;

/**
 * Geri dönüşü olmayan iş için onay penceresi. Tek bir soru gösterir; her düğme
 * kendi işini yapar ve pencereyi kapatır.
 */
const confirmBox = {
  open({ title, body, actions }) {
    $("#confirmTitle").textContent = title;
    $("#confirmBody").textContent = body;
    const box = $("#confirmActions");
    box.innerHTML = "";
    for (const a of actions) {
      const b = document.createElement("button");
      b.className = "btn" + (a.primary ? " btn-go" : "");
      b.textContent = a.label;
      b.addEventListener("click", () => { confirmBox.close(); a.run?.(); });
      box.appendChild(b);
    }
    $("#confirm").hidden = false;
    $("#confirmCancel").focus();
  },
  close() { $("#confirm").hidden = true; },
  isOpen: () => !$("#confirm").hidden
};

/** Ayarlar penceresi; menüden ve Ctrl+, ile açılır. */
const settings = {
  open() { $("#settings").hidden = false; $("#settingsClose").focus(); },
  close() { $("#settings").hidden = true; },
  isOpen: () => !$("#settings").hidden
};
let route = null;
let trails = [];        // tamamlanmış uçuşların küre üzerindeki izleri
let hoverAP = null;     // imlecin üzerindeki havalimanı
let pendingO = null;    // kalkış seçildi, varış bekleniyor
let drag = null;

/* ---------------- rota ---------------- */

/** Türetilmiş uçuş durumu; her okuyuşta güncel. */
const st = () => flightState(db, route, timer);
const done = () => st().done;
const arrived = () => st().arrived;

function setRoute(key, quiet) {
  const next = buildRoute(key);
  if (!next) return false;

  route = next;
  db.route = key;
  globe.aim(route.center[0], route.center[1], Boolean(quiet));

  if (!quiet) {
    timer.pause();
    timer.setPhase("focus");
  }
  $("#flightNo").textContent = route.flightNo;
  picker?.sync();
  persist();
  renderAll();
  return true;
}

/* ---------------- sayaç ---------------- */

const durationOf = phase => durationMsOf(db, phase);

const timer = createTimer({
  durationOf,
  canStart: phase => !(phase === "focus" && arrived())
});

/**
 * Faz doldu: mesafeyi işle, günlüğe yaz, sıradaki faza geç.
 * @param skipped Atla ile geçildiyse odak süresi toplama eklenmez.
 */
function finishPhase(skipped = false) {
  const remainingAtEntry = timer.remaining;
  if (timer.phase === "focus") {
    const before = st();

    // Varılmış rotada kazanılacak mesafe kalmadı. Buraya yine de girilebiliyor
    // (molada Atla, sonra tekrar Atla); eskiden her seferinde toplam km'ye bir
    // bacak ekliyordu — iki dokunuşla bedava mesafe. Artık hiçbir şey eklemez.
    if (before.arrived) {
      timer.setPhase("focus");
      persist();
      renderAll();
      return;
    }

    // Oturumun kazandırdığı mesafe: 25 dakika bir bacak, 50 dakika iki.
    const earned = kmForFocus(route, db.focusMin);
    const flown = Math.min(route.km, before.flownOnRoute + earned);
    db.progress[route.key] = flown;
    // Atlanan pomodoro odak sayılmaz: 25 dakikalığı üçüncü saniyede atlayınca
    // "toplam odak" 25 dakika artmamalı. Mesafe ise işler.
    if (!skipped) db.totalMs += durationOf("focus");
    // Varışta artakalan pay toplama girmesin: gerçekten alınan yol kadar.
    db.totalKm += flown - before.flownOnRoute;
    db.cycle = (db.cycle + 1) % 4;

    const arrivedNow = flown >= route.km - 0.001;
    if (arrivedNow) db.log.unshift({ r: route.key, km: Math.round(route.km), t: Date.now() });

    // Günlüğe zaman damgalı kayıt: gün dağılımı, seri ve saat histogramı
    // ancak buradan çıkıyor. Gerçekte odakta geçen süre yazılır.
    //
    // Atlanan oturum hiç yazılmaz. Uçak yine ilerliyor ama bu bir çalışma
    // kaydı değil; günlüğe girseydi "atlandı" işaretiyle bile saat
    // histogramını ve seriyi kirletirdi.
    if (!skipped) {
      const focusMs = Math.max(0, Math.min(durationOf("focus"), durationOf("focus") - remainingAtEntry));
      const record = focusRecord({
        time: Date.now(),
        routeKey: route.key,
        minutes: Math.floor(focusMs / 60000),
        km: flown - before.flownOnRoute
      });
      journal.append(record);
      records.push(record);
    }

    if (db.sound) chime(arrivedNow ? "land" : "level");
    if (arrivedNow) {
      platform.notify(
        "İniş yapıldı · " + route.dName,
        route.o + " → " + route.d + " tamamlandı · " + nf(route.km) + " km. Günlüğe damga düştü."
      );
    } else {
      const left = Math.max(1, Math.ceil((route.km - flown) / route.kmPerLeg));
      platform.notify(
        "Pomodoro tamam · mola",
        nf(flown - before.flownOnRoute) + " km yol aldın · " +
        route.dName + " için " + left + " pomodoro kaldı."
      );
    }

    timer.setPhase(db.cycle === 0 ? "long" : "break");
    if (db.autoBreak && !arrivedNow) timer.start();
  } else {
    if (db.sound) chime("boarding");
    timer.setPhase("focus");
    platform.notify("Mola bitti", "Kabin hazır — seyre devam edebilirsin.");
  }
  persist();
  rebuildTrails();
  renderAll();
}

/**
 * Uçuş ancak bilet koparılınca başlar. Bilet rotaya bağlı: yeni bir rota
 * seçilince yeniden kesilir, yarım kalmış uçuşta tekrar sorulmaz.
 */
function needsTicket() {
  if (!route || timer.running) return false;
  if (timer.phase !== "focus") return false;
  const v = st();
  if (v.started || v.arrived) return false;
  return db.ticketFor !== route.key;
}

function openTicket() {
  ticketScreen.open(issueTicket(route, db, st()));
}

/**
 * Tamamlanmış rotayı yeniden uçmak: yalnız bu rotanın ilerlemesi silinir.
 * Günlük, toplam mil ve damgalar durur — yapılmış uçuş yapılmıştır.
 */
/**
 * Rotayı başa alır: yalnız bu rotanın ilerlemesi ve bileti sıfırlanır.
 * Günlük, toplam mil ve damgalar durur — uçulmuş pomodoro uçulmuştur.
 *
 * @param withTicket Bilet ekranı hemen açılsın mı (tekrar uçarken açılır,
 *                   sıfırlama onayından sonra kullanıcıyı zorlamaz).
 */
function resetFlight(withTicket) {
  db.progress[route.key] = 0;
  if (db.ticketFor === route.key) db.ticketFor = "";
  timer.pause();
  timer.setPhase("focus");
  persist();
  renderAll();
  if (withTicket) openTicket();
}

const flyAgain = () => resetFlight(true);

/**
 * Sıfırlama onayı. İki ayrı iş var: geri sayımı baştan almak zararsız, uçuşu
 * sıfırlamak kat edilen yolu siliyor — soruyu sorup ikisini ayırıyoruz.
 */
function askReset() {
  const v = st();
  const body = v.started
    ? fmtPom(v.flownOnRoute / route.kmPerLeg) + " pomodoro (" + nf(v.flownOnRoute) +
      " km) silinir. Günlüğe yazılmış odaklar, toplam mil ve damgalar durur."
    : "Bu rotada henüz yol alınmadı; yalnız sayaç başa döner.";

  confirmBox.open({
    title: route.o + " → " + route.d + " sıfırlansın mı?",
    body,
    actions: [
      { label: "Yalnız sayaç", run: () => { timer.reset(); renderAll(); } },
      { label: "Uçuşu sıfırla", primary: true, run: () => resetFlight(false) }
    ]
  });
}

/** Sayacın kendisi de saklanır: uygulama kapanıp açılınca seyir kaldığı yerden sürer. */
function persist() {
  db.phase = timer.phase;
  db.running = timer.running;
  db.endAt = timer.endAt;
  db.remainingMs = timer.remaining;
  save();
}

/** Rotanın kat edilen oranı; devam eden pomodoro da orantılı sayılır. */
const flownFraction = () => st().fraction;

/* ---------------- çizim ---------------- */

function renderControls() {
  const btn = $("#goBtn");
  // İniş yapılmış rotada düğme tek işe yarar: baştan uçmak. Mola sürerken
  // "Beklet" önceliklidir, molanın kendisi hâlâ işleyen bir faz.
  if (arrived() && !timer.running) {
    btn.textContent = "Tekrar uç";
    btn.disabled = false;
    return;
  }
  btn.textContent = timer.running
    ? "Beklet"
    : timer.phase === "focus" ? (done() > 0 ? "Devam" : "Kalkış") : "Molayı başlat";
  btn.disabled = false;
}

function renderHud() {
  const v = st();
  const f = v.fraction, flown = v.flownKm;
  $("#oCode").textContent = route.o;
  $("#oCity").textContent = route.oName;
  $("#dCode").textContent = route.d;
  $("#dCity").textContent = route.dName;
  $("#rFlown").innerHTML = nf(flown) + "<small>km</small>";
  $("#rLeft").innerHTML = nf(v.remainingKm) + "<small>km</small>";
  $("#rAlt").innerHTML = nf(v.altitudeM) + "<small>m</small>";
  $("#rEta").innerHTML = v.legsLeft + "<small>pomodoro</small>";

  const dot = $("#stDot"), stEl = $("#stText");
  dot.className = "dot";
  if (v.arrived) {
    stEl.textContent = "İniş yapıldı · " + route.dName;
  } else if (timer.running && timer.phase === "focus") {
    dot.classList.add("live");
    stEl.textContent = "Seyir halinde · " + nf(route.kmPerLeg) + " km/pom";
  } else if (timer.running) {
    dot.classList.add("rest");
    stEl.textContent = "Mola · kabin ışıkları kısık";
  } else if (v.started) {
    stEl.textContent = "Beklemede · rotada tutuluyor";
  } else {
    stEl.textContent = "Kalkışa hazır";
  }
}

function renderTimer() {
  $("#clock").textContent = formatClock(timer.remaining);
  $("#phase").textContent =
    timer.phase === "focus" ? "Odak · Seyir"
      : timer.phase === "long" ? "Uzun mola · Aktarma"
      : "Mola · Kabin";

  const v = st();
  let sub;
  if (v.arrived) {
    sub = route.dName + "'a indin. Günlüğe damga düştü — yeni bir rota seç ya da tekrar uç.";
  } else if (timer.phase === "focus") {
    // Oturum 25 dakikanın katı değilse kaç pomodoro ettiğini söylemek gerekiyor;
    // "1,5 pomodoro" yarım kalan kutuyu açıklıyor.
    const poms = db.focusMin / POMODORO_MIN;
    const label = poms === 1 ? "Bu pomodoro " : "Bu oturum " + fmtPom(poms) + " pomodoro, ";
    sub = label + nf(kmForFocus(route, db.focusMin)) + " km taşıyor.";
  } else {
    sub = "Uçak rotada bekliyor; mola bitince seyre devam.";
  }
  $("#subline").textContent = sub;

  const cy = $("#cycle");
  cy.innerHTML = "";
  for (let i = 0; i < 4; i++) {
    const el = document.createElement("i");
    if (i < db.cycle) el.className = "on";
    cy.appendChild(el);
  }

  document.title = timer.running
    ? formatClock(timer.remaining) + " · " +
      (timer.phase === "focus" ? route.o + "→" + route.d : "Mola") + " · Uçuş Modu"
    : "Uçuş Modu";

  renderControls();
  syncPlatform({
    progress: timer.running ? Math.round(100 * timer.elapsed()) / 100 : -1,
    // Pencereden bakarken ekran, sayaç dursa bile kapanmamalı.
    keepAwake: (timer.running && timer.phase === "focus") || Boolean(windowView?.isOpen())
  });
}

function renderLegs() {
  const box = $("#legs");
  box.innerHTML = "";
  const v = st();
  const d = v.done;
  for (let i = 0; i < route.legs; i++) {
    const el = document.createElement("i");
    if (i < d) el.className = "done";
    else if (i === d && v.legFill > 0.001) {
      el.className = timer.running && timer.phase === "focus" ? "now part" : "part";
      // CSSOM: satır içi style özniteliği CSP'ye takılıyor, özellik ataması takılmıyor.
      el.style.setProperty("--fill", (v.legFill * 100).toFixed(1) + "%");
    }
    box.appendChild(el);
  }
  $("#legAux").textContent = d + " / " + route.legs;
  $("#legNote").textContent = v.arrived
    ? "Rota tamamlandı · " + nf(route.km) + " km"
    : (route.legs - d) + " pomodoro sonra " + route.dName + "'a iniyorsun · toplam " + nf(route.km) + " km";
}

function renderRoutes() {
  const list = $("#routeList");
  list.innerHTML = "";
  for (const [o, d, nick] of PRESETS) {
    const key = o + "-" + d;
    const km = kmBetween(o, d);
    if (km === null) continue;
    const legs = legsFor(km);
    const flownKm = Math.min(db.progress[key] || 0, km);
    const pr = Math.floor(flownKm / (km / legs) + 1e-9);

    const b = document.createElement("button");
    b.className = "route";
    b.setAttribute("aria-current", key === route.key ? "true" : "false");
    b.innerHTML =
      '<span class="pair">' + o + "<span>→</span>" + d + "</span>" +
      '<span class="nick">' + nick + "</span>" +
      '<span class="km">' + nf(km) + " km · " + legs + " pom" + (pr ? " · " + pr + "✓" : "") + "</span>";
    b.addEventListener("click", () => { setRoute(key); $("#msg").textContent = ""; picker?.sync(); });
    list.appendChild(b);
  }
}

function renderLog() {
  const ul = $("#logs");
  ul.innerHTML = "";
  $("#logEmpty").style.display = db.log.length ? "none" : "block";
  for (const e of db.log.slice(0, 8)) {
    const [o, d] = e.r.split("-");
    const li = document.createElement("li");
    li.innerHTML =
      '<span class="st">' + o + "→" + d + "</span>" +
      "<span>" + cityOf(d) + "</span>" +
      '<span class="dt">' + new Date(e.t).toLocaleDateString("tr-TR") + "</span>";
    ul.appendChild(li);
  }
  $("#sHours").textContent = (db.totalMs / 3600000).toFixed(1).replace(".", ",") + " sa";
  $("#sKm").textContent = nf(db.totalKm) + " km";
}

function renderAll() {
  renderHud();
  renderTimer();
  renderLegs();
  renderRoutes();
  renderLog();
  // Biniş kartı önizlemesi de duruma bağlı ("Bilet kes" / "Tekrar uç");
  // yalnızca rota değişince tazelenince varış sonrası eski hâlinde kalıyordu.
  picker?.sync();
}

/* ---------------- döngü ---------------- */

/** Güneş konumu dakikada bir tazelenir; gündüz/gece sınırı buna göre çizilir. */
let sun = subsolarPoint();
let sunAt = Date.now();

let lastFrame = performance.now();

function scene() {
  const highlighted = new Set([route?.o, route?.d, hoverAP, pendingO].filter(Boolean));
  return {
    route,
    fraction: flownFraction(),
    places: registry.isLoaded ? registry.all() : [],
    highlighted,
    world,
    sun,
    trails
  };
}

function loop(now = performance.now()) {
  const dt = Math.min(0.05, (now - lastFrame) / 1000);
  lastFrame = now;

  if (timer.running) {
    if (timer.sync()) finishPhase(false);
    else { renderTimer(); renderHud(); renderLegs(); }
  }
  if (now - sunAt > 60000) { sun = subsolarPoint(); sunAt = now; }

  globe.ease(dt);
  globe.draw(scene());
  requestAnimationFrame(loop);
}

/* ---------------- küreden rota seçimi ---------------- */

function mapHint() {
  $("#mapHint").textContent = pendingO
    ? cityOf(pendingO) + " → varış seç (Esc: vazgeç)"
    : "Küreden seç · sürükleyerek çevir · tekerlekle yakınlaştır";
}

function setCursor() {
  $("#map").style.cursor = drag && drag.moved ? "grabbing" : hoverAP ? "pointer" : "grab";
}

function pickAirport(code) {
  if (!code) {
    if (pendingO) { pendingO = null; mapHint(); }
    return;
  }
  if (!pendingO || code === pendingO) {
    pendingO = pendingO === code ? null : code;
    $("#msg").textContent = pendingO ? cityOf(code) + " kalkış olarak seçildi — şimdi varışa tıkla." : "";
    mapHint();
    return;
  }
  const o = pendingO;
  pendingO = null;
  if (!setRoute(o + "-" + code)) { mapHint(); return; }
  picker?.sync();
  $("#msg").textContent =
    cityOf(o) + " → " + cityOf(code) + " yüklendi · " + nf(route.km) + " km · " + route.legs + " pomodoro.";
  mapHint();
}

/* ---------------- olay bağlama ---------------- */

function wireControls() {
  $("#goBtn").addEventListener("click", () => {
    if (arrived() && !timer.running) { flyAgain(); return; }
    if (needsTicket()) { openTicket(); return; }
    timer.toggle();
    renderAll();
  });
  $("#resetBtn").addEventListener("click", () => askReset());
  $("#skipBtn").addEventListener("click", () => { timer.pause(); finishPhase(true); });

  $("#soundBtn").addEventListener("click", e => {
    db.sound = !db.sound;
    persist();
    e.currentTarget.setAttribute("aria-pressed", String(db.sound));
    e.currentTarget.textContent = db.sound ? "Kabin sesi" : "Sessiz";
  });

  $("#clearBtn").addEventListener("click", () => {
    store.clearProgress();
    journal.clear();
    records = [];
    timer.pause();
    timer.setPhase("focus");
    rebuildTrails();
    renderAll();
  });

  $("#autoBreak").addEventListener("change", e => {
    db.autoBreak = e.currentTarget.checked;
    persist();
  });

  $("#confirmCancel").addEventListener("click", () => confirmBox.close());
  $("#confirm").addEventListener("click", e => {
    if (e.target === $("#confirm")) confirmBox.close();
  });

  $("#settingsClose").addEventListener("click", () => settings.close());
  $("#settings").addEventListener("click", e => {
    if (e.target === $("#settings")) settings.close();
  });

  $("#passenger").addEventListener("input", e => {
    db.passenger = e.currentTarget.value.slice(0, 28);
    persist();
  });
}

/** Sayısal olmayan, süreye dokunmayan basit seçim çubuğu. */
function wireChoice(id, key, onChange) {
  const box = $(id);
  box.addEventListener("click", e => {
    const b = e.target.closest("button");
    if (!b) return;
    db[key] = b.dataset.v;
    persist();
    [...box.children].forEach(x => x.setAttribute("aria-pressed", String(x === b)));
    onChange?.(b.dataset.v);
  });
  [...box.children].forEach(x => x.setAttribute("aria-pressed", String(x.dataset.v === db[key])));
}

/** Süre ayarlarının sınırları: dakika. */
const DURATION_LIMITS = { focusMin: [1, 180], breakMin: [1, 60] };

/** Aynı süre ayarının ekrandaki bütün kopyaları. */
const durSegs = key => document.querySelectorAll('[data-dur="' + key + '"]');
const durFields = key => document.querySelectorAll('[data-dur-field="' + key + '"]');

/**
 * Süreyi uygular. Hazır seçenekler de elle yazılan değer de buradan geçer.
 * Rota yeniden kurulmaz — bacak sayısı artık süreden bağımsız; yalnız
 * bacakların dolma hızı değişir.
 */
function applyDuration(key, value, phase) {
  const [min, max] = DURATION_LIMITS[key];
  const n = Math.round(Number(value));
  if (!Number.isFinite(n)) return false;
  const clamped = Math.max(min, Math.min(max, n));

  db[key] = clamped;
  // Süre değişimi yalnızca o fazda ve sayaç dururken kalan süreyi tazeler.
  if (!timer.running && timer.phase === phase) timer.refreshDuration();
  persist();
  syncDurations();
  picker?.sync();
  renderAll();
  return clamped === n;
}

/**
 * Seçenek düğmeleri ve sayı alanları tek bir değeri gösterir. Odak süresi iki
 * yerde birden duruyor (giriş ekranı ve ayarlar penceresi); ikisi de buradan
 * eşitleniyor, biri değişince öbürü de güncelleniyor.
 */
function syncDurations() {
  for (const key of ["focusMin", "breakMin"]) {
    const value = db[key];
    let preset = false;
    for (const seg of durSegs(key)) {
      for (const b of seg.children) {
        const on = +b.dataset.v === value;
        b.setAttribute("aria-pressed", String(on));
        if (on) preset = true;
      }
    }
    for (const field of durFields(key)) {
      if (document.activeElement !== field) field.value = String(value);
      field.dataset.custom = String(!preset);
    }
  }
}

function wireDuration(key, phase) {
  for (const seg of durSegs(key)) {
    seg.addEventListener("click", e => {
      const b = e.target.closest("button");
      if (b) applyDuration(key, b.dataset.v, phase);
    });
  }

  for (const field of durFields(key)) {
    // Yazarken her tuşta uygulanmaz: "5" yazıp "50" yapacakken araya girmesin.
    field.addEventListener("change", () => applyDuration(key, field.value, phase));
    field.addEventListener("blur", () => syncDurations());
    field.addEventListener("keydown", e => {
      if (e.key === "Enter") { e.preventDefault(); field.blur(); }
    });
  }
}

function wireRoutePicker() {
  picker = createRoutePicker({
    getData: () => db,
    getRoute: () => route,
    onLoad: key => {
      if (!setRoute(key)) { $("#msg").textContent = "Bu rota kurulamadı."; return; }
      const [o, d] = key.split("-");
      $("#msg").textContent =
        cityOf(o) + " → " + cityOf(d) + " yüklendi · " + nf(route.km) + " km · " + route.legs + " pomodoro.";
    },
    onIssue: () => {
      if (arrived()) flyAgain();
      else if (needsTicket()) openTicket();
      else { timer.start(); renderAll(); }
    }
  });
}

function wireGlobe() {
  const cv = $("#map");
  const pointers = new Map();   // aktif temas noktaları — iki parmak yakınlaştırması için
  let pinchDist = 0;
  let wasPinch = false;

  const spread = () => {
    const [a, b] = [...pointers.values()];
    return Math.hypot(a.x - b.x, a.y - b.y);
  };
  /** Parmak ucu fare imlecinden kaba; isabet yarıçapı buna göre. */
  const reachOf = e => (e.pointerType && e.pointerType !== "mouse" ? 26 : 15);
  const redraw = () => globe.draw(scene());

  cv.addEventListener("pointerdown", e => {
    if (e.button !== 0) return;
    try { cv.setPointerCapture(e.pointerId); } catch { /* sentetik olay */ }
    globe.stopMomentum();     // yeni dokunuşta süren atalet kesilir
    pointers.set(e.pointerId, { x: e.offsetX, y: e.offsetY });
    if (pointers.size === 2) {
      drag = null;                 // iki parmak: çevirme değil, yakınlaştırma
      wasPinch = true;
      pinchDist = spread();
      hoverAP = null;
      return;
    }
    drag = { x: e.offsetX, y: e.offsetY, moved: false };
    wasPinch = false;
  });

  cv.addEventListener("pointermove", e => {
    const mx = e.offsetX, my = e.offsetY;
    if (pointers.has(e.pointerId)) pointers.set(e.pointerId, { x: mx, y: my });

    if (pointers.size === 2) {
      const d = spread();
      if (pinchDist > 0 && d > 0) {
        const [a, b] = [...pointers.values()];
        globe.zoomAnchored((a.x + b.x) / 2, (a.y + b.y) / 2, d / pinchDist);
      }
      pinchDist = d;
      setCursor();
      return;
    }

    if (drag) {
      const dx = mx - drag.x, dy = my - drag.y;
      if (!drag.moved && dx * dx + dy * dy > 16) drag.moved = true;
      if (drag.moved) {
        globe.rotateByPixels(dx, dy);
        drag.x = mx; drag.y = my;
        hoverAP = null;
      }
    }
    if (!drag || !drag.moved) hoverAP = globe.placeAt(mx, my, reachOf(e));
    setCursor();
  });

  function endPointer(e) {
    pointers.delete(e.pointerId);
    if (pointers.size < 2) pinchDist = 0;
    try { cv.releasePointerCapture(e.pointerId); } catch { /* sentetik olay */ }
  }

  cv.addEventListener("pointerup", e => {
    const wasDrag = drag && drag.moved;
    if (wasDrag) globe.releaseMomentum();
    const pinched = wasPinch || pointers.size > 1;
    endPointer(e);
    drag = null;
    if (pointers.size === 0) wasPinch = false;
    if (!wasDrag && !pinched) pickAirport(globe.placeAt(e.offsetX, e.offsetY, reachOf(e)));
    hoverAP = e.pointerType && e.pointerType !== "mouse" ? null : globe.placeAt(e.offsetX, e.offsetY);
    setCursor();
  });

  cv.addEventListener("pointercancel", e => { endPointer(e); drag = null; hoverAP = null; setCursor(); });
  cv.addEventListener("pointerleave", () => { hoverAP = null; setCursor(); });

  // fare tekerleği / touchpad ile yakınlaştırma
  // tekerlek imlecin altındaki noktayı yerinde tutarak yakınlaştırır
  cv.addEventListener("wheel", e => {
    e.preventDefault();
    globe.zoomAnchored(e.offsetX, e.offsetY, e.deltaY < 0 ? 1.12 : 1 / 1.12);
  }, { passive: false });

  cv.addEventListener("dblclick", e => {
    if (globe.placeAt(e.offsetX, e.offsetY)) return;   // seçim çift tıkla çakışmasın
    pendingO = null;
    globe.resetView();
    globe.aim(route.center[0], route.center[1]);
    mapHint();
  });

  const ro = new ResizeObserver(() => { globe.resize(); redraw(); });
  ro.observe(cv);
}

function wireKeyboard() {
  document.addEventListener("keydown", e => {
    if (e.key === "Escape" && confirmBox.isOpen()) { confirmBox.close(); return; }
    if (e.key === "Escape" && windowView?.isOpen()) { windowView.close(); return; }
    if (e.key === "Escape" && settings.isOpen()) { settings.close(); return; }
    if (e.key === "Escape" && ticketScreen?.isOpen()) { ticketScreen.close(); return; }
    if (e.key === "Escape" && statement?.isOpen()) { statement.close(); return; }
    if (e.key === "Escape" && pendingO) {
      pendingO = null;
      $("#msg").textContent = "";
      mapHint();
      return;
    }
    // Menüdeki kısayolların klavye karşılığı; üst düzey menü öğeleri
    // hızlandırıcı kaydetmiyor.
    if (e.ctrlKey && e.key === ",") { e.preventDefault(); settings.open(); return; }
    if (e.ctrlKey && (e.key === "i" || e.key === "I")) { e.preventDefault(); statement.open(); return; }

    if (e.target && e.target.matches && e.target.matches("input,select,button")) return;
    if (e.code === "Space") {
      e.preventDefault();
      timer.toggle();
      renderAll();
    }
  });
}

function wirePlatform() {
  platform.onCommand(cmd => {
    if (cmd === "toggle") timer.toggle();
    else if (cmd === "reset") { askReset(); return; }
    else if (cmd === "skip") { timer.pause(); finishPhase(true); return; }
    else if (cmd === "sound") { $("#soundBtn").click(); return; }
    else if (cmd === "clear") { $("#clearBtn").click(); return; }
    else if (cmd === "settings") { settings.open(); return; }
    else if (cmd === "stats") { statement.open(); return; }
    renderAll();
  });
}

function wireTheme() {
  window.matchMedia("(prefers-color-scheme: dark)")
    .addEventListener("change", () => globe.readTokens());
  new MutationObserver(() => globe.readTokens())
    .observe(document.documentElement, { attributes: true, attributeFilter: ["data-theme"] });
}

/* ---------------- açılış ---------------- */

/**
 * Saklanmış sayacı geri kurar. Uygulama kapalıyken faz dolduysa duvar saatinden
 * anlaşılır ve bitiş orada işlenir.
 */
function restoreTimer() {
  timer.setPhase(db.phase === "break" || db.phase === "long" ? db.phase : "focus");
  timer.remaining = Math.max(0, Number(db.remainingMs) || durationOf(timer.phase));
  if (db.running && db.endAt > 0) {
    timer.running = true;
    timer.endAt = db.endAt;
    if (timer.sync()) finishPhase(false);
  }
}

/** Yer listesi ve kara parçaları arka planda yüklenir; gelince yeniden çizilir. */
/** Günlükteki tamamlanmış rotaları çizilebilir yollara çevirir. */
function rebuildTrails() {
  const seen = new Set();
  trails = [];
  for (const entry of db.log) {
    if (seen.has(entry.r)) continue;
    seen.add(entry.r);
    const r = buildRoute(entry.r);
    if (r) trails.push(samplePath(r, 96));
  }
}

async function loadData() {
  const [, , stored] = await Promise.all([
    ensurePlacesLoaded(loadBinary),
    world.loadBase().catch(() => {}),  // kara katmanı gelmezse tel küre kalır
    journal.read()
  ]);
  records = stored;
  if (route) route = buildRoute(route.key) || route;
  rebuildTrails();
  picker?.sync();
  renderAll();
}

function boot() {
  $("#autoBreak").checked = db.autoBreak;
  $("#passenger").value = db.passenger ?? "";
  $("#soundBtn").setAttribute("aria-pressed", String(db.sound));
  $("#soundBtn").textContent = db.sound ? "Kabin sesi" : "Sessiz";

  ticketScreen = createTicketScreen({
    onTorn: () => {
      db.ticketFor = route.key;
      persist();
      // Koparılan bileti bir an göster, sonra kalkışa geç.
      setTimeout(() => {
        ticketScreen.close();
        timer.start();
        renderAll();
      }, 700);
    },
    onTick: () => { if (db.sound) chime("level"); }
  });

  windowView = createWindowView({
    getData: () => db,
    getScene: () => {
      const v = st();
      const at = pointAt(route, Math.max(0, Math.min(0.9999, v.fraction)));
      return {
        route,
        fraction: v.fraction,
        altitudeM: v.altitudeM,
        world,
        places: registry.isLoaded ? registry.visibleAt(1) : [],
        remaining: timer.remaining,
        running: timer.running,
        phaseLabel: timer.phase === "focus" ? "Odak · Seyir"
          : timer.phase === "long" ? "Uzun mola · Aktarma" : "Mola · Kabin",
        cameraLat: at[0],
        cameraLon: at[1]
      };
    },
    onToggle: () => { timer.toggle(); renderAll(); },
    onSkip: () => { timer.pause(); finishPhase(true); },
    onModeChange: next => { db.windowMode = next; persist(); },
    onPickVideo: () => platform.pickVideo()
  });

  $("#windowBtn").addEventListener("click", () => windowView.open());

  statement = createStatement({
    getRecords: () => records,
    getData: () => db,
    getWorld: () => world
  });

  wireControls();
  wireChoice("#segWing", "wingModel");
  wireChoice("#segSeat", "seatSide");
  // Kalite değişince sahne yeniden kurulur; pencere açıkken de anında geçerli olur.
  wireChoice("#segQuality", "renderQuality", value => windowView?.applyQuality(value));
  wireDuration("focusMin", "focus");
  wireDuration("breakMin", "break");
  syncDurations();
  wireRoutePicker();
  wireGlobe();
  wireKeyboard();
  wirePlatform();
  wireTheme();

  globe.readTokens();
  globe.resize();
  mapHint();

  if (!setRoute(db.route, true)) setRoute("IST-JFK", true);
  picker.sync();

  restoreTimer();
  renderAll();
  requestAnimationFrame(loop);
  loadData();
}

boot();
