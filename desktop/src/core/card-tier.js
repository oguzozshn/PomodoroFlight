/**
 * Havayolu kart seviyesi. Eşikler dünya turuna bağlanır: bir tur Silver,
 * beş tur Elite, on iki buçuk tur Elite Plus.
 */

import { EARTH_CIRCUMFERENCE_KM } from "./estimates.js";

export const TIERS = [
  { key: "CLASSIC", title: "Classic", minKm: 0 },
  { key: "SILVER", title: "Silver", minKm: EARTH_CIRCUMFERENCE_KM },
  { key: "ELITE", title: "Elite", minKm: EARTH_CIRCUMFERENCE_KM * 5 },
  { key: "ELITE_PLUS", title: "Elite Plus", minKm: EARTH_CIRCUMFERENCE_KM * 12.5 }
];

export function tierOf(km) {
  let current = TIERS[0];
  for (const tier of TIERS) if (km >= tier.minKm) current = tier;
  return current;
}

export function nextTierOf(km) {
  const index = TIERS.indexOf(tierOf(km)) + 1;
  return index < TIERS.length ? TIERS[index] : null;
}

/** Bulunulan seviyeden bir sonrakine ilerleme (0..1); en üstte 1. */
export function tierProgress(km) {
  const current = tierOf(km);
  const next = nextTierOf(km);
  if (!next) return 1;
  const span = next.minKm - current.minKm;
  if (span <= 0) return 1;
  return Math.max(0, Math.min(1, (km - current.minKm) / span));
}

/** Bir sonraki seviyeye kalan mesafe; en üstte 0. */
export function kmToNextTier(km) {
  const next = nextTierOf(km);
  return next ? Math.max(0, next.minKm - km) : 0;
}
