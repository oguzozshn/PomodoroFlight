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
import { startFlight, endFlight, flightTrails } from "../core/flights.js";
import { journalStorage } from "../platform/journal-storage.js";
import { subsolarPoint } from "../core/sun.js";
import { platform, syncPlatform } from "../platform/index.js";
import { loadBinary } from "../platform/assets.js";
import { chime } from "../platform/chime.js";
import { createGlobe } from "./globe.js";
import { createStatement } from "./logbook.js";
import { createTicketScreen } from "./ticket.js";
import { createRoutePicker } from "./route-picker.js";
import { logRow } from "./log-row.js";
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
  onCancel: null,
  open({ title, body, actions, onCancel = null }) {
    confirmBox.onCancel = onCancel;
    $("#confirmTitle").textContent = title;
    $("#confirmBody").textContent = body;
    const box = $("#confirmActions");
    box.innerHTML = "";
    for (const a of actions) {
      const b = document.createElement("button");
      b.className = "btn" + (a.primary ? " btn-go" : "");
      b.textContent = a.label;
      b.addEventListener("click", () => { confirmBox.onCancel = null; confirmBox.close(); a.run?.(); });
      box.appendChild(b);
    }
    $("#confirm").hidden = false;
    $("#confirmCancel").focus();
  },
  close() { $("#confirm").hidden = true; },
  /** Vazgeç, Esc ya da dışına tıklama: pencere kapanır, çağıranın kancası çalışır. */
  cancel() {
    const hook = confirmBox.onCancel;
    confirmBox.onCancel = null;
    confirmBox.close();
    hook?.();
  },
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
  // Açılıştaki sessiz kurulum kaydetmez: o an sayaç henüz geri yüklenmedi ve
  // kaydetmek, süren oturumun bitiş anını sıfır bir sayaçla ezerdi. Uygulama
  // oturum sürerken kapanıp açılınca sayaç 25:00'a dönüyordu.
  if (!quiet) persist();
  renderAll();
  return true;
}

/* ---------------- sayaç ---------------- */

const durationOf = phase => durationMsOf(db, phase);

/**
 * Sayaç yalnız süren bir uçuşta işler: bilet kesilmeden odak başlamaz. Space,
 * menüdeki "Başlat / Beklet" ve pencereden bakıştaki düğme hep buradan geçiyor.
 */
const timer = createTimer({
  durationOf,
  canStart: phase => flightActive() && !(phase === "focus" && arrived())
});

/**
 * Faz doldu: mesafeyi işle, günlüğe yaz, sıradaki faza geç.
 * Odak oturumu atlanamaz: bu yol yalnız süre dolunca ya da molada "Molayı atla"
 * ile çağrılır.
 */
function finishPhase() {
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
    // Sayaç bilet olmadan başlamıyor; bu yalnız eski bir kayıttan geri yüklenen
    // oturum için bir güvence.
    if (db.flight?.r !== route.key) startFlight(db, route.key);
    db.flight.flown = flown;
    db.totalMs += durationOf("focus");
    // Varışta artakalan pay toplama girmesin: gerçekten alınan yol kadar.
    db.totalKm += flown - before.flownOnRoute;
    db.cycle = (db.cycle + 1) % 4;

    const arrivedNow = flown >= route.km - 0.001;
    if (arrivedNow) endFlight(db, route, { done: true });

    // Günlüğe zaman damgalı kayıt: gün dağılımı, seri ve saat histogramı
    // ancak buradan çıkıyor. Gerçekte odakta geçen süre yazılır.
    const focusMs = Math.max(0, Math.min(durationOf("focus"), durationOf("focus") - remainingAtEntry));
    const record = focusRecord({
      time: Date.now(),
      routeKey: route.key,
      minutes: Math.floor(focusMs / 60000),
      km: flown - before.flownOnRoute
    });
    journal.append(record);
    records.push(record);

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

function openTicket() {
  ticketScreen.open(issueTicket(route, db, st()));
}

/**
 * Tamamlanmış rotayı yeniden uçmak: yalnız bu rotanın ilerlemesi silinir.
 * Günlük, toplam mil ve damgalar durur — yapılmış uçuş yapılmıştır.
 */
/**
 * İniş yapılmış rotayı yeniden uçmak. Varış günlüğe zaten yazıldı; inmiş uçuş
 * bırakılır, yeni uçuş bilet koparılınca sıfırdan başlar.
 */
function flyAgain() {
  if (db.flight?.r === route.key) db.flight = null;
  timer.pause();
  timer.setPhase("focus");
  persist();
  renderAll();
  openTicket();
}

/** Uçulan kısmın kısa özeti: "7,5 / 23 pomodoro (2.656 km)". */
const flownSummary = (r, flown) =>
  fmtPom(flown / r.kmPerLeg) + " / " + r.legs + " pomodoro (" + nf(flown) + " km)";

/**
 * Bu rotada süren (inmemiş) bir uçuş var mı? Süre kilidi, sayaç düğmeleri ve
 * sayaç izni hep bu tek soruya bakıyor.
 */
function flightActive() {
  return Boolean(route && db.flight?.r === route.key && !db.flight.landed);
}

/** Bu rotada bitirilebilecek bir uçuş var mı? Bilet koparılmış olması yeter. */
function canEnd() {
  return flightActive() && !st().arrived;
}

/** Yalnız mola atlanabilir; odak oturumu atlanamaz. */
function skipBreak() {
  if (timer.phase === "focus" || !flightActive()) return;
  timer.pause();
  finishPhase();
}

/**
 * Uçuşu yarıda bitirir. Süren oturum sayılmaz: uçuş son tamamlanan oturumun
 * bittiği yerde kapanır ve günlüğe yarım olarak düşer.
 */
function endActiveFlight() {
  timer.pause();
  timer.setPhase("focus");
  endFlight(db, route, { done: false });
  persist();
  rebuildTrails();
  renderAll();
}

function askEnd() {
  if (!canEnd()) return;
  const running = timer.phase === "focus" && timer.elapsed() > 0;
  const flown = st().flownOnRoute;
  confirmBox.open({
    title: route.o + " → " + route.d + " uçuşu bitirilsin mi?",
    body: (flown > 0
      ? flownSummary(route, flown) + " uçuldu; kalan kısım uçulmayacak. Uçuş günlüğe yarım olarak yazılır"
      : "Henüz yol alınmadı; uçuş günlüğe yazılmadan kapanır") +
      (running ? ", süren oturum sayılmaz." : "."),
    actions: [{ label: "Bitir", primary: true, run: endActiveFlight }]
  });
}

/**
 * Rota değişiminin tek kapısı. Yarım uçuş varken başka rota seçilirse sorar:
 * onaylanırsa uçuş yarım olarak günlüğe düşer, yeni rota sıfırdan açılır.
 * Kaybedilecek yol yoksa (uçuş yok, inmiş ya da henüz km alınmamış) sormaz.
 *
 * @param then (ok) => void — rota gerçekten değişti mi; vazgeçilirse false.
 */
function requestRoute(key, then = () => {}) {
  const f = db.flight;
  const current = f ? buildRoute(f.r) : null;
  const atRisk = Boolean(f && f.r !== key && !f.landed && f.flown > 0 &&
    current && f.flown < current.km - 0.001);

  if (!atRisk) {
    if (f && f.r !== key) db.flight = null;
    then(setRoute(key));
    return;
  }

  const next = buildRoute(key);
  confirmBox.open({
    title: current.o + " → " + current.d + " uçuşu bitirilsin mi?",
    body: flownSummary(current, f.flown) + " uçuldu. " +
      (next ? next.o + " → " + next.d : "Yeni rota") + " sıfırdan başlayacak.",
    actions: [{
      label: "Bitir ve geç", primary: true,
      run: () => {
        timer.pause();
        timer.setPhase("focus");
        endFlight(db, current, { done: false });
        rebuildTrails();
        then(setRoute(key));
      }
    }],
    onCancel: () => then(false)
  });
}

/**
 * Başlat/beklet: sayacı çevirir ve hemen kaydeder. Beklet, Devam, Space, menü ve
 * pencereden bakış hep buradan geçiyor; kaydetmeyi unutan tek bir yol kalmasın.
 */
function toggleTimer() {
  timer.toggle();
  persist();
  renderAll();
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

let durationsLocked = null;   // son çizimdeki kilit durumu; değişince alanlar eşitlenir

/**
 * Sayaç panelinin düğmeleri yalnız uçuş sürerken var. Uçuşu başlatan (Bilet
 * kes), bitiren (Uçuşu bitir) ve yeniden uçuran (Tekrar uç) biniş kartında.
 */
function renderControls() {
  // Uçuş sekmesi iki durumlu: bilet koparılınca (ve inişten sonra, "Tekrar uç"a
  // kadar) sayaç ve uçuş planı; öncesinde rota, süreler ve biniş kartı.
  $("#flightView").dataset.mode = route && db.flight?.r === route.key ? "fly" : "prep";
  const active = flightActive() && !arrived();
  if (flightActive() !== durationsLocked) {
    durationsLocked = flightActive();
    syncDurations();
  }
  $("#controls").hidden = !active;
  if (!active) return;
  $("#goBtn").textContent = timer.running
    ? "Beklet"
    : timer.phase === "focus" ? "Devam" : "Molayı başlat";
  $("#skipBtn").hidden = timer.phase === "focus";
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
  if (!v.arrived && !flightActive()) {
    sub = "Rota seç, süreleri ayarla, bileti kes.";
  } else if (v.arrived) {
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
    // Yalnız süren uçuşun rotasında "devam ediyor" işareti; diğerleri sıfırdan başlar.
    const flownKm = db.flight?.r === key && !db.flight.landed ? Math.min(db.flight.flown, km) : 0;
    const pr = Math.floor(flownKm / (km / legs) + 1e-9);

    const b = document.createElement("button");
    b.className = "route";
    b.setAttribute("aria-current", key === route.key ? "true" : "false");
    b.innerHTML =
      '<span class="pair">' + o + "<span>→</span>" + d + "</span>" +
      '<span class="nick">' + nick + "</span>" +
      '<span class="km">' + nf(km) + " km · " + legs + " pom" + (pr ? " · " + pr + "✓" : "") + "</span>";
    b.addEventListener("click", () => requestRoute(key, () => { $("#msg").textContent = ""; picker?.sync(); }));
    list.appendChild(b);
  }
}

function renderLog() {
  const ul = $("#logs");
  ul.innerHTML = "";
  $("#logEmpty").style.display = db.log.length ? "none" : "block";
  // Kendi sekmesinde duruyor, yer sıkıntısı yok: bütün uçuşlar listelenir.
  for (const e of db.log) ul.appendChild(logRow(e));
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
    if (timer.sync()) finishPhase();
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
  requestRoute(o + "-" + code, ok => {
    picker?.sync();
    if (ok) {
      $("#msg").textContent =
        cityOf(o) + " → " + cityOf(code) + " yüklendi · " + nf(route.km) + " km · " + route.legs + " pomodoro.";
    }
    mapHint();
  });
}

/* ---------------- sekmeler ---------------- */

/** Uçuş ve uçuş günlüğü sekmeleri. Küre gizliyken boyutu sıfırlanır; geri
 *  gelince boyut gözlemcisi yeniden ölçer. */
function showTab(name) {
  const log = name === "log";
  $("#flightView").hidden = log;
  $("#logView").hidden = !log;
  $("#tabFlight").setAttribute("aria-selected", String(!log));
  $("#tabLog").setAttribute("aria-selected", String(log));
}

/* ---------------- olay bağlama ---------------- */

function wireControls() {
  // Yalnız uçuş sürerken görünür: bekletir, devam ettirir, molayı başlatır.
  $("#goBtn").addEventListener("click", () => toggleTimer());
  $("#resetBtn").addEventListener("click", () => { timer.reset(); persist(); renderAll(); });
  $("#skipBtn").addEventListener("click", () => skipBreak());

  $("#soundBtn").addEventListener("click", e => {
    db.sound = !db.sound;
    persist();
    e.currentTarget.setAttribute("aria-pressed", String(db.sound));
    e.currentTarget.textContent = db.sound ? "Kabin sesi" : "Sessiz";
  });

  $("#tabFlight").addEventListener("click", () => showTab("flight"));
  $("#tabLog").addEventListener("click", () => showTab("log"));

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

  $("#confirmCancel").addEventListener("click", () => confirmBox.cancel());
  $("#confirm").addEventListener("click", e => {
    if (e.target === $("#confirm")) confirmBox.cancel();
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
const DURATION_LIMITS = { focusMin: [1, 180], breakMin: [1, 60], longBreakMin: [1, 60] };

/** Aynı süre ayarının ekrandaki bütün kopyaları. */
const durSegs = key => document.querySelectorAll('[data-dur="' + key + '"]');
const durFields = key => document.querySelectorAll('[data-dur-field="' + key + '"]');

/**
 * Süreyi uygular. Hazır seçenekler de elle yazılan değer de buradan geçer.
 * Rota yeniden kurulmaz — bacak sayısı artık süreden bağımsız; yalnız
 * bacakların dolma hızı değişir.
 */
function applyDuration(key, value, phase) {
  // Süreler uçuştan önce ayarlanır; uçuş sürerken kilitli.
  if (flightActive()) { syncDurations(); return false; }
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
  const locked = flightActive();
  for (const key of ["focusMin", "breakMin", "longBreakMin"]) {
    const value = db[key];
    let preset = false;
    for (const seg of durSegs(key)) {
      for (const b of seg.children) {
        const on = +b.dataset.v === value;
        b.setAttribute("aria-pressed", String(on));
        b.disabled = locked;
        if (on) preset = true;
      }
    }
    for (const field of durFields(key)) {
      if (document.activeElement !== field) field.value = String(value);
      field.dataset.custom = String(!preset);
      field.disabled = locked;
    }
  }
  $("#durLock").hidden = !locked;
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
    onLoad: key => requestRoute(key, ok => {
      picker?.sync();
      if (!ok) {
        // Vazgeçildiyse sessiz kal; rota kurulamadıysa söyle.
        if (!buildRoute(key)) $("#msg").textContent = "Bu rota kurulamadı.";
        return;
      }
      const [o, d] = key.split("-");
      $("#msg").textContent =
        cityOf(o) + " → " + cityOf(d) + " yüklendi · " + nf(route.km) + " km · " + route.legs + " pomodoro.";
    }),
    // Biniş kartının tek düğmesi: bilet kes · uçuşu bitir · tekrar uç.
    onIssue: () => {
      if (arrived()) flyAgain();
      else if (flightActive()) askEnd();
      else openTicket();
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
    if (e.key === "Escape" && confirmBox.isOpen()) { confirmBox.cancel(); return; }
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
      toggleTimer();
    }
  });
}

function wirePlatform() {
  platform.onCommand(cmd => {
    if (cmd === "toggle") { toggleTimer(); return; }
    else if (cmd === "reset") { timer.reset(); persist(); }
    else if (cmd === "end") { askEnd(); return; }
    else if (cmd === "skip") { skipBreak(); return; }
    else if (cmd === "sound") { $("#soundBtn").click(); return; }
    else if (cmd === "clear") { $("#clearBtn").click(); return; }
    else if (cmd === "settings") { settings.open(); return; }
    else if (cmd === "stats") { statement.open(); return; }
    else if (cmd === "log") { showTab("log"); return; }
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
    if (timer.sync()) finishPhase();
  }
}

/** Yer listesi ve kara parçaları arka planda yüklenir; gelince yeniden çizilir. */
/** Günlükteki tamamlanmış rotaları çizilebilir yollara çevirir. */
/** Tamamlanan uçuşlar tam yay, yarım bırakılanlar yalnız uçulan kısım. */
function rebuildTrails() {
  trails = flightTrails(db.log, 96);
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
      startFlight(db, route.key);
      timer.setPhase("focus");
      persist();
      // Koparılan bileti bir an göster, sonra kalkışa geç.
      setTimeout(() => {
        ticketScreen.close();
        timer.start();
        persist();
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
        phase: timer.phase,
        active: flightActive() && !arrived(),
        phaseLabel: timer.phase === "focus" ? "Odak · Seyir"
          : timer.phase === "long" ? "Uzun mola · Aktarma" : "Mola · Kabin",
        cameraLat: at[0],
        cameraLon: at[1]
      };
    },
    onToggle: () => toggleTimer(),
    onSkip: () => skipBreak(),
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
  wireDuration("longBreakMin", "long");
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
