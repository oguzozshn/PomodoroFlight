/**
 * Gömülü ikili varlıkları okur (`src/assets/data/*.bin`).
 *
 * Masaüstünde bunlar `app://` şeması altından geliyor, mobil ya da web
 * kabuğunda aynı göreli yol http üzerinden çalışır — bu yüzden fetch yeterli.
 */

const BASE = "assets/data/";
const cache = new Map();

/** Adı verilen varlığı ArrayBuffer olarak döndürür; aynı dosya iki kez okunmaz. */
export function loadBinary(name) {
  let pending = cache.get(name);
  if (!pending) {
    pending = fetch(new URL(BASE + name, document.baseURI))
      .then(res => {
        if (!res.ok) throw new Error(name + " okunamadı: " + res.status);
        return res.arrayBuffer();
      })
      .catch(err => { cache.delete(name); throw err; });
    cache.set(name, pending);
  }
  return pending;
}
