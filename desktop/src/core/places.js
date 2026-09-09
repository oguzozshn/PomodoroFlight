/**
 * Uçuş yapılabilen yerler: OurAirports'un kamu malı verisinden süzülmüş,
 * tarifeli seferi ve IATA kodu olan 3.941 havalimanı.
 *
 * Android sürümündeki data/PlaceFormat.kt + data/PlaceRegistry.kt karşılığı;
 * `places.bin` dosyası (`UMP2`) olduğu gibi okunuyor.
 *
 * Elle yazılmış Türkçe adlar (core/airports.js) veri setinin üzerinde kalır.
 */

import { AP as CURATED } from "./airports.js";
import { TURKISH_CITIES } from "./turkish-cities.js";

const MAGIC = "UMP2";
const LON_SCALE = 180 / 32767;
const LAT_SCALE = 90 / 32767;

/** rank: 0 büyük, 1 orta, 2 küçük havalimanı. */
const utf8 = new TextDecoder("utf-8");
const ascii = new TextDecoder("ascii");

/** `UMP2` tamponunu yer listesine çevirir. */
export function parsePlaces(buffer) {
  const view = new DataView(buffer);
  const bytes = new Uint8Array(buffer);
  const magic = ascii.decode(bytes.subarray(0, 4));
  if (magic !== MAGIC) throw new Error("Beklenmeyen yer listesi biçimi: " + magic);

  let off = 4;
  const count = view.getInt32(off, true); off += 4;
  const places = new Array(count);

  for (let i = 0; i < count; i++) {
    const lon = view.getInt16(off, true) * LON_SCALE; off += 2;
    const lat = view.getInt16(off, true) * LAT_SCALE; off += 2;
    const rank = view.getUint8(off); off += 1;
    const code = ascii.decode(bytes.subarray(off, off + 3)); off += 3;
    const country = ascii.decode(bytes.subarray(off, off + 2)); off += 2;
    const cityLen = view.getUint8(off); off += 1;
    const city = utf8.decode(bytes.subarray(off, off + cityLen)); off += cityLen;
    const nameLen = view.getUint8(off); off += 1;
    const airportName = utf8.decode(bytes.subarray(off, off + nameLen)); off += nameLen;
    places[i] = { code, city, lat, lon, country, rank, airportName };
  }
  return places;
}

/**
 * Aramayı aksanlardan ve Türkçe büyük/küçük harf tuhaflıklarından bağımsız kılar:
 * "istanbul" → İstanbul, "diyarbakir" → Diyarbakır.
 */
const FOLD = {
  "ı": "i", "î": "i", "í": "i", "ì": "i", "ï": "i",
  "ş": "s", "ğ": "g",
  "ü": "u", "û": "u", "ù": "u", "ú": "u",
  "ö": "o", "ô": "o", "ò": "o", "ó": "o", "õ": "o",
  "ç": "c",
  "â": "a", "á": "a", "à": "a", "ä": "a", "ã": "a", "å": "a",
  "é": "e", "è": "e", "ê": "e", "ë": "e",
  "ñ": "n"
};

export function normalizeForSearch(text) {
  let out = "";
  for (const ch of text.toLocaleLowerCase("tr")) out += FOLD[ch] ?? ch;
  return out;
}

/** ISO ülke kodunu Türkçe ülke adına çevirir ("TR" → "Türkiye"). */
let regionNames = null;
export function countryName(iso) {
  if (!iso || iso.length !== 2) return iso || "";
  try {
    regionNames = regionNames || new Intl.DisplayNames(["tr"], { type: "region" });
    return regionNames.of(iso) || iso;
  } catch {
    return iso;
  }
}

/** Gösterilecek ad: önce elle yazılmış Türkçe ad, sonra sözlük, en son veri seti. */
export function preferredName(place) {
  return CURATED[place.code]?.[0] ?? TURKISH_CITIES[place.code] ?? place.city;
}

export function createRegistry() {
  let loaded = [];
  let byCode = new Map();
  let searchKeys = [];       // her yer için aranabilir, normalize edilmiş adlar

  /** Varlıktan okunan listeyi devreye alır. */
  function install(places) {
    loaded = places.map(p => ({ ...p, city: preferredName(p) }));
    byCode = new Map(loaded.map(p => [p.code, p]));
    searchKeys = loaded.map(place => {
      const raw = [place.city];
      if (place.airportName) raw.push(place.airportName);
      if (CURATED[place.code]) raw.push(CURATED[place.code][0]);
      if (TURKISH_CITIES[place.code]) raw.push(TURKISH_CITIES[place.code]);
      return [...new Set(raw.map(normalizeForSearch))];
    });
  }

  /**
   * Kodu çözer. Elle girilmiş Türkçe ad ve koordinat varsa o kazanır; ülke ve
   * büyüklük bilgisi veri setinden tamamlanır.
   */
  function find(code) {
    const c = CURATED[code];
    const d = byCode.get(code);
    if (c && d) {
      return { code, city: c[0], lat: c[1], lon: c[2], country: d.country, rank: d.rank, airportName: d.airportName };
    }
    if (c) return { code, city: c[0], lat: c[1], lon: c[2], country: "", rank: 0, airportName: "" };
    return d ?? null;
  }

  /**
   * Şehir adı, havalimanı adı ya da IATA koduna göre arar.
   * Başıyla eşleşenler önce, ardından büyük havalimanları gelir.
   */
  function search(query, limit = 30) {
    const q = normalizeForSearch(query.trim());
    if (!q) return [];
    const upper = q.toUpperCase();
    const hits = [];

    for (let i = 0; i < loaded.length; i++) {
      const place = loaded[i];
      let best = Infinity;
      if (place.code === upper) best = 0;
      for (const key of searchKeys[i]) {
        const score = key.startsWith(q) ? 1 : key.includes(q) ? 3 : Infinity;
        if (score < best) best = score;
      }
      if (best === Infinity) continue;
      hits.push([best * 4 + place.rank, place]);
    }

    hits.sort((a, b) => a[0] - b[0] || a[1].city.length - b[1].city.length);
    return hits.slice(0, limit).map(h => h[1]);
  }

  return {
    install,
    find,
    search,
    all: () => loaded,
    get isLoaded() { return loaded.length > 0; },
    /** Küre üzerinde bu ölçekte gösterilecek yerler. */
    visibleAt(zoom) {
      const maxRank = zoom >= 3 ? 2 : zoom >= 1.7 ? 1 : 0;
      return loaded.filter(p => p.rank <= maxRank);
    }
  };
}

/** Uygulamanın tanıdığı tüm yerler — Kotlin'deki PlaceRegistry nesnesinin karşılığı. */
export const registry = createRegistry();

/** Yer listesini varlıktan okuyup kayıt defterine yükler. */
export async function ensurePlacesLoaded(loadBinary) {
  if (registry.isLoaded) return;
  try {
    registry.install(parsePlaces(await loadBinary("places.bin")));
  } catch {
    /* liste okunamadı — elle yazılmış havalimanlarıyla devam edilir */
  }
}
