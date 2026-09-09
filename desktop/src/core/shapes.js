/**
 * Kara parçaları ve göller: Natural Earth verisinin kompakt ikili biçimi.
 *
 * Android sürümündeki data/ShapeFormat.kt ile aynı `UMC1` biçimini okur —
 * dosyalar olduğu gibi kullanılıyor, yeniden üretmeye gerek yok.
 * `tools/build_shapes.py` GeoJSON'u int16 kuantalamayla bu biçime çeviriyor
 * (yeryüzünde en fazla ~340 m sapma).
 *
 * Noktalar birim küre üzerinde (x, y, z) olarak saklanır; böylece her karede
 * trigonometri yerine yalnızca iç çarpım hesaplanır ve arkada kalan parçalar
 * tek bir karşılaştırmayla elenir.
 */

const MAGIC = "UMC1";
const LON_SCALE = 180 / 32767;
const LAT_SCALE = 90 / 32767;

/** Tek bir kara/göl parçası. */
export class WorldShape {
  constructor(xyz, cx, cy, cz, sinRadius, cosRadius) {
    this.xyz = xyz;
    this.cx = cx; this.cy = cy; this.cz = cz;
    this.sinRadius = sinRadius;
    this.cosRadius = cosRadius;
  }

  get pointCount() { return this.xyz.length / 3; }

  /** Parça tamamen kürenin arkasındaysa hiç çizilmesin diye kaba eleme. */
  isHidden(ux, uy, uz) {
    return this.cx * ux + this.cy * uy + this.cz * uz < -this.sinRadius;
  }

  /**
   * Parça, (ux, uy, uz) yönünde açılan cosCap kosinuslu koniyle kesişiyor mu?
   * Pencere görüntüsünde ufka kadar olan dar dilimi elemek için.
   */
  withinCap(ux, uy, uz, cosCap) {
    const dot = this.cx * ux + this.cy * uy + this.cz * uz;
    const sinCap = Math.sqrt(Math.max(0, 1 - cosCap * cosCap));
    // cos(kapAçısı + parçaYarıçapı); -1'in altına inerse koni tüm küreyi kapsar.
    const cosSum = cosCap * this.cosRadius - sinCap * this.sinRadius;
    return cosSum <= -1 || dot >= cosSum;
  }
}

/** Parçanın merkezini ve kapsadığı açısal yarıçapı hesaplar (eleme için). */
function buildShape(xyz, sx, sy, sz) {
  const length = Math.sqrt(sx * sx + sy * sy + sz * sz);
  if (length < 1e-6) {
    // Küreyi baştan başa geçen parça: elenmesin.
    return new WorldShape(xyz, 0, 0, 1, 1, -1);
  }
  const cx = sx / length, cy = sy / length, cz = sz / length;

  let minDot = 1;
  for (let i = 0; i < xyz.length; i += 3) {
    const dot = xyz[i] * cx + xyz[i + 1] * cy + xyz[i + 2] * cz;
    if (dot < minDot) minDot = dot;
  }
  const cosR = Math.max(-1, Math.min(1, minDot));
  const sinR = Math.sqrt(Math.max(0, 1 - cosR * cosR));
  return new WorldShape(xyz, cx, cy, cz, sinR, cosR);
}

/** `UMC1` tamponunu WorldShape dizisine çevirir. */
export function parseShapes(buffer) {
  const view = new DataView(buffer);
  const magic = String.fromCharCode(view.getUint8(0), view.getUint8(1), view.getUint8(2), view.getUint8(3));
  if (magic !== MAGIC) throw new Error("Beklenmeyen kıyı şeridi biçimi: " + magic);

  let off = 4;
  const lineCount = view.getInt32(off, true); off += 4;
  const shapes = [];

  for (let s = 0; s < lineCount; s++) {
    const pointCount = view.getInt32(off, true); off += 4;
    const xyz = new Float32Array(pointCount * 3);
    let sx = 0, sy = 0, sz = 0;

    for (let i = 0; i < pointCount; i++) {
      const lon = view.getInt16(off, true) * LON_SCALE; off += 2;
      const lat = view.getInt16(off, true) * LAT_SCALE; off += 2;
      const p = lat * Math.PI / 180, l = lon * Math.PI / 180;
      const cp = Math.cos(p);
      const x = cp * Math.cos(l), y = cp * Math.sin(l), z = Math.sin(p);
      xyz[i * 3] = x; xyz[i * 3 + 1] = y; xyz[i * 3 + 2] = z;
      sx += x; sy += y; sz += z;
    }
    shapes.push(buildShape(xyz, sx, sy, sz));
  }
  return shapes;
}

/**
 * Kara katmanları. `land_50m` yalnızca yakınlaştırınca (ölçek ≥ 2,2) yükleniyor;
 * 243 KB'lık dosyayı açılışta okumanın anlamı yok.
 */
export function createWorld(load) {
  let land110 = null, lakes110 = null, land50 = null, land50Pending = null;

  return {
    /** Açılışta gereken hafif katmanlar. */
    async loadBase() {
      const [a, b] = await Promise.all([load("land_110m.bin"), load("lakes_110m.bin")]);
      land110 = parseShapes(a);
      lakes110 = parseShapes(b);
    },

    /** Yakınlaştırınca çağrılır; aynı anda birden çok istek açılmaz. */
    ensureDetail() {
      if (land50 || land50Pending) return land50Pending;
      land50Pending = load("land_50m.bin")
        .then(buf => { land50 = parseShapes(buf); land50Pending = null; })
        .catch(() => { land50Pending = null; });   // ayrıntı gelmezse kaba katman kalır
      return land50Pending;
    },

    /** Verilen ölçek için uygun kara katmanı. */
    land(zoom) {
      if (zoom >= 2.2) {
        this.ensureDetail();
        if (land50) return land50;
      }
      return land110 || [];
    },

    lakes() { return lakes110 || []; },
    get ready() { return land110 !== null; }
  };
}
