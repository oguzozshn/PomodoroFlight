/**
 * Uçuş yüksekliğinden dışarı bakan iğne delikli (pinhole) kamera.
 *
 * Bütün hesaplar dünya merkezli birim vektörlerle yapılır: bir noktanın
 * ekrandaki yeri, kamera tabanına alınan üç iç çarpım ve tek bölmeden ibarettir.
 * Böylece ufuk eğrisi, kara parçaları, bulutlar ve güneş aynı tek yoldan geçer.
 *
 * Uzunluk birimi dünya yarıçapıdır: yer yüzeyi 1, kamera 1 + irtifa/R uzaklıkta.
 */

import { RAD } from "../../core/geo.js";

/** Dünya yarıçapı (km). */
export const EARTH_RADIUS_KM = 6371;

export function createSkyCamera({
  lat, lon, altitudeKm, azimuthDeg, pitchDeg, fovDeg, width, height
}) {
  const phi = lat * RAD, lambda = lon * RAD;
  const az = azimuthDeg * RAD, pitch = pitchDeg * RAD;

  const cp = Math.cos(phi), sp = Math.sin(phi);
  const cl = Math.cos(lambda), sl = Math.sin(lambda);

  /** Uçağın altındaki nokta; aynı zamanda yerel "yukarı" yönü. */
  const upX = cp * cl, upY = cp * sl, upZ = sp;
  const eastX = -sl, eastY = cl, eastZ = 0;
  const northX = -sp * cl, northY = -sp * sl, northZ = cp;

  const sa = Math.sin(az), ca = Math.cos(az);
  /** Bakışın yatay bileşeni. */
  const hfX = sa * eastX + ca * northX;
  const hfY = sa * eastY + ca * northY;
  const hfZ = sa * eastZ + ca * northZ;

  /** Ekranın sağı: yatay bakışın 90 derece doğusu. */
  const rightX = ca * eastX - sa * northX;
  const rightY = ca * eastY - sa * northY;
  const rightZ = ca * eastZ - sa * northZ;

  const cpi = Math.cos(pitch), spi = Math.sin(pitch);
  const fwdX = cpi * hfX + spi * upX;
  const fwdY = cpi * hfY + spi * upY;
  const fwdZ = cpi * hfZ + spi * upZ;

  const camUpX = -spi * hfX + cpi * upX;
  const camUpY = -spi * hfY + cpi * upY;
  const camUpZ = -spi * hfZ + cpi * upZ;

  /** Kameranın dünya merkezine göre konumu (yarıçap birimiyle). */
  const scale = 1 + altitudeKm / EARTH_RADIUS_KM;
  const posX = scale * upX, posY = scale * upY, posZ = scale * upZ;

  /** Dikey görüş açısından türetilen odak uzaklığı (piksel). */
  const focal = (height / 2) / Math.tan((fovDeg / 2) * RAD);
  const cx = width / 2, cy = height / 2;

  /**
   * Ufuk çemberi: yüzeyde görülebilen en uzak noktaların `W · up` değeri.
   * Bundan küçük olan her yüzey noktası dünyanın arkasında kalır.
   */
  const horizonCos = 1 / scale;
  const horizonDip = Math.acos(Math.max(-1, Math.min(1, horizonCos)));

  const tanHalfVertical = Math.tan((fovDeg / 2) * RAD);

  return {
    lat, lon, altitudeKm, width, height, focal,
    upX, upY, upZ, posX, posY, posZ,
    horizonCos, horizonDip,
    /** Ufka olan yatay uzaklık (km) — puslanma ve bulut mesafesi için. */
    horizonKm: EARTH_RADIUS_KM * horizonDip,
    azimuthRad: az,
    tanHalfVertical,
    tanHalfHorizontal: tanHalfVertical * (width / height),

    /** Vektörün bakış doğrultusundaki bileşeni; sıfırdan büyükse kameranın önünde. */
    depth: (x, y, z) => x * fwdX + y * fwdY + z * fwdZ,
    lateral: (x, y, z) => x * rightX + y * rightY + z * rightZ,
    vertical: (x, y, z) => x * camUpX + y * camUpY + z * camUpZ,

    /** Kameradan çıkan bir vektörün ekranda düştüğü yer. */
    screenX: (x, y, z, depth) => cx + focal * ((x * rightX + y * rightY + z * rightZ) / depth),
    screenY: (x, y, z, depth) => cy - focal * ((x * camUpX + y * camUpY + z * camUpZ) / depth),

    /** Yerel yataydan `elevation`, kuzeyden `bearing` radyan açılı yönün birim vektörü. */
    direction(elevation, bearing) {
      const ce = Math.cos(elevation), se = Math.sin(elevation);
      const sb = Math.sin(bearing), cb = Math.cos(bearing);
      const hx = sb * eastX + cb * northX;
      const hy = sb * eastY + cb * northY;
      const hz = sb * eastZ + cb * northZ;
      return [ce * hx + se * upX, ce * hy + se * upY, ce * hz + se * upZ];
    },

    /** Yer yüzeyinde, uçaktan `angle` radyan uzakta, `bearing` yönündeki nokta. */
    surfaceAt(angle, bearing) {
      const s = Math.sin(angle), c = Math.cos(angle);
      const sb = Math.sin(bearing), cb = Math.cos(bearing);
      const hx = sb * eastX + cb * northX;
      const hy = sb * eastY + cb * northY;
      const hz = sb * eastZ + cb * northZ;
      return [c * upX + s * hx, c * upY + s * hy, c * upZ + s * hz];
    },

    /** Ekrandaki bir noktadan çıkan bakış ışınının birim vektörü (izdüşümün tersi). */
    unproject(x, y) {
      const a = (x - cx) / focal, b = (cy - y) / focal;
      const dx = fwdX + a * rightX + b * camUpX;
      const dy = fwdY + a * rightY + b * camUpY;
      const dz = fwdZ + a * rightZ + b * camUpZ;
      const len = Math.hypot(dx, dy, dz);
      return [dx / len, dy / len, dz / len];
    }
  };
}
