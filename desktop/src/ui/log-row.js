/**
 * Uçuş günlüğünün tek satırı. Ana ekrandaki kısa liste ile istatistik
 * ekranındaki tam liste aynı biçimi kullanıyor; iki yerde ayrı yazılmasın.
 *
 *   tamamlanan:  IST→JFK  New York  ✓ 23 pom · 8.026 km        13.09.2026
 *   yarım:       IST→JFK  New York  %30 · 7 / 23 pom · 2.444 km 13.09.2026
 */

import { registry } from "../core/places.js";
import { buildRoute } from "../core/route.js";
import { logEntryOf } from "../core/flights.js";
import { formatDate } from "../core/format.js";

const nf = n => Math.round(n).toLocaleString("tr-TR");
const pom = n => n.toLocaleString("tr-TR", { maximumFractionDigits: 1 });

const span = (className, text) => {
  const el = document.createElement("span");
  if (className) el.className = className;
  el.textContent = text;
  return el;
};

/** Satırın özet metni; test ve erişilebilirlik için ayrıca dışa açık. */
export function describeEntry(raw) {
  const e = logEntryOf(raw);
  const route = buildRoute(e.r);
  const km = e.km ?? route?.km ?? e.flown;
  if (e.done) {
    return "✓ " + (route ? route.legs + " pom · " : "") + nf(km) + " km";
  }
  const pct = km > 0 ? Math.floor((100 * e.flown) / km) : 0;
  const poms = route ? pom(e.flown / route.kmPerLeg) + " / " + route.legs + " pom · " : "";
  return "%" + pct + " · " + poms + nf(e.flown) + " km";
}

export function logRow(raw) {
  const e = logEntryOf(raw);
  const [o, d] = e.r.split("-");
  const li = document.createElement("li");
  if (!e.done) {
    li.className = "partial";
    li.title = "Yarıda bırakıldı";
  }
  li.append(
    span("st", o + "→" + d),
    span("", registry.find(d)?.city ?? d),
    span("pr", describeEntry(raw)),
    span("dt", formatDate(e.t))
  );
  return li;
}
