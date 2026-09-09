/**
 * Güneşin o an dünyanın neresinde tam tepede olduğunu hesaplar.
 * Kürenin gündüz/gece sınırını ve pencereden görünen gökyüzünün rengini
 * çizmeye yeter — düşük hassasiyetli, ~0,01° mertebesinde.
 *
 * Android sürümündeki data/Sun.kt'nin birebir karşılığı; SunTest.kt'deki
 * gündönümü ve ekinoks değerlerine karşı doğrulanır.
 */

import { RAD, DEG, wrapLon } from "./geo.js";

/** 1 Ocak 2000, 12:00 UTC — astronomik hesapların başlangıç anı. */
const J2000_MILLIS = 946_728_000_000;
const DAY_MILLIS = 86_400_000;

/** Güneşin tam tepede olduğu nokta: [enlem, boylam]. */
export function subsolarPoint(timeMillis = Date.now()) {
  const days = (timeMillis - J2000_MILLIS) / DAY_MILLIS;

  // ortalama boylam ve ortalama anomali
  const meanLongitude = ((280.460 + 0.9856474 * days) % 360) * RAD;
  const meanAnomaly = ((357.528 + 0.9856003 * days) % 360) * RAD;

  // ekliptik boylam: merkez denklemiyle düzeltilmiş
  const eclipticLongitude =
    meanLongitude + 1.915 * RAD * Math.sin(meanAnomaly) + 0.020 * RAD * Math.sin(2 * meanAnomaly);
  const obliquity = (23.439 - 0.0000004 * days) * RAD;

  const declination = Math.asin(Math.sin(obliquity) * Math.sin(eclipticLongitude));
  const rightAscension = Math.atan2(
    Math.cos(obliquity) * Math.sin(eclipticLongitude),
    Math.cos(eclipticLongitude)
  );

  // Greenwich yıldız zamanı: dünyanın o andaki dönme açısı
  const siderealTime = ((280.46061837 + 360.98564736629 * days) % 360) * RAD;

  return [declination * DEG, wrapLon((rightAscension - siderealTime) * DEG)];
}

/**
 * Verilen noktada güneşin ufka göre yüksekliği (derece).
 * Negatifse güneş batmıştır; -18°'nin altı astronomik gecedir.
 */
export function sunElevation(lat, lon, timeMillis = Date.now()) {
  const [sLat, sLon] = subsolarPoint(timeMillis);
  const p1 = lat * RAD, p2 = sLat * RAD, dLon = (sLon - lon) * RAD;
  const cosZenith =
    Math.sin(p1) * Math.sin(p2) + Math.cos(p1) * Math.cos(p2) * Math.cos(dLon);
  return Math.asin(Math.max(-1, Math.min(1, cosZenith))) * DEG;
}
