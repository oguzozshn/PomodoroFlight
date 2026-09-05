package com.oguzozsahin.ucusmodu.ui.window

import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/** Dunya yaricapi (km). */
const val EARTH_RADIUS_KM = 6371.0

/**
 * Ucus yuksekliginden disari bakan igne delikli (pinhole) kamera.
 *
 * Butun hesaplar dunya merkezli birim vektorlerle yapilir: bir noktanin ekrandaki
 * yeri, kamera tabanina alinan uc ic carpim ve tek bolmeden ibarettir. Boylece
 * ufuk egrisi, kara parcalari, bulutlar ve gunes ayni tek yoldan gecer.
 *
 * Uzunluk birimi dunya yaricapidir: yer yuzeyi 1, kamera 1 + irtifa/R uzakliktadir.
 */
class SkyCamera(
    val lat: Double,
    val lon: Double,
    /** Deniz seviyesinden yukseklik (km). */
    val altitudeKm: Double,
    /** Bakis yonu: 0 kuzey, 90 dogu. */
    val azimuthDeg: Double,
    /** Bakis egimi: negatif deger asagi bakar. */
    val pitchDeg: Double,
    /** Dikey gorus acisi (derece). */
    val fovDeg: Double,
    val width: Float,
    val height: Float,
) {
    private val phi = Math.toRadians(lat)
    private val lambda = Math.toRadians(lon)
    private val az = Math.toRadians(azimuthDeg)
    private val pitch = Math.toRadians(pitchDeg)

    /** Ucagin altindaki nokta; ayni zamanda yerel "yukari" yonu. */
    val upX = cos(phi) * cos(lambda)
    val upY = cos(phi) * sin(lambda)
    val upZ = sin(phi)

    private val eastX = -sin(lambda)
    private val eastY = cos(lambda)
    private val eastZ = 0.0

    private val northX = -sin(phi) * cos(lambda)
    private val northY = -sin(phi) * sin(lambda)
    private val northZ = cos(phi)

    /** Bakisin yatay bileseni. */
    private val hfX = sin(az) * eastX + cos(az) * northX
    private val hfY = sin(az) * eastY + cos(az) * northY
    private val hfZ = sin(az) * eastZ + cos(az) * northZ

    /** Ekranin sagi: yatay bakisin 90 derece dogusu. */
    private val rightX = cos(az) * eastX - sin(az) * northX
    private val rightY = cos(az) * eastY - sin(az) * northY
    private val rightZ = cos(az) * eastZ - sin(az) * northZ

    /** Bakis dogrultusu ve ekranin yukarisi; egim kadar cevrilmis hali. */
    private val fwdX = cos(pitch) * hfX + sin(pitch) * upX
    private val fwdY = cos(pitch) * hfY + sin(pitch) * upY
    private val fwdZ = cos(pitch) * hfZ + sin(pitch) * upZ

    private val camUpX = -sin(pitch) * hfX + cos(pitch) * upX
    private val camUpY = -sin(pitch) * hfY + cos(pitch) * upY
    private val camUpZ = -sin(pitch) * hfZ + cos(pitch) * upZ

    /** Kameranin dunya merkezine gore konumu (yaricap birimiyle). */
    private val scale = 1.0 + altitudeKm / EARTH_RADIUS_KM
    val posX = scale * upX
    val posY = scale * upY
    val posZ = scale * upZ

    /** Dikey gorus acisindan turetilen odak uzakligi (piksel). */
    val focal = (height / 2f) / tan(Math.toRadians(fovDeg / 2.0)).toFloat()

    private val cx = width / 2f
    private val cy = height / 2f

    /**
     * Ufuk cemberi: yuzeyde gorulebilen en uzak noktalarin `W . up` degeri.
     * Bundan kucuk olan her yuzey noktasi dunyanin arkasinda kalir.
     */
    val horizonCos = 1.0 / scale

    /** Ufkun yerel yataydan asagi sapmasi (radyan). */
    val horizonDip = acos(horizonCos.coerceIn(-1.0, 1.0))

    /** Ufka olan yatay uzaklik (km) — puslanma ve bulut mesafesi icin. */
    val horizonKm = EARTH_RADIUS_KM * horizonDip

    /** Kameradan cikan bir vektorun ekranda dustugu yer. */
    fun screenX(vx: Double, vy: Double, vz: Double, depth: Double): Float =
        cx + focal * ((vx * rightX + vy * rightY + vz * rightZ) / depth).toFloat()

    fun screenY(vx: Double, vy: Double, vz: Double, depth: Double): Float =
        cy - focal * ((vx * camUpX + vy * camUpY + vz * camUpZ) / depth).toFloat()

    /** Vektorun bakis dogrultusundaki bileseni; sifirdan buyukse kameranin onunde. */
    fun depth(vx: Double, vy: Double, vz: Double): Double =
        vx * fwdX + vy * fwdY + vz * fwdZ

    /** Vektorun ekranin sagi yonundeki bileseni. */
    fun lateral(vx: Double, vy: Double, vz: Double): Double =
        vx * rightX + vy * rightY + vz * rightZ

    /** Vektorun ekranin yukarisi yonundeki bileseni. */
    fun vertical(vx: Double, vy: Double, vz: Double): Double =
        vx * camUpX + vy * camUpY + vz * camUpZ

    /** Gorus piramidinin yaridan tanjantlari; kirpma duzlemleri bunlardan cikar. */
    val tanHalfVertical = tan(Math.toRadians(fovDeg / 2.0))
    val tanHalfHorizontal = tanHalfVertical * (width / height).toDouble()

    /** Yerel yataydan [elevation], kuzeyden [bearing] radyan acili yonun birim vektoru. */
    fun direction(elevation: Double, bearing: Double): DoubleArray {
        val ce = cos(elevation)
        val hx = sin(bearing) * eastX + cos(bearing) * northX
        val hy = sin(bearing) * eastY + cos(bearing) * northY
        val hz = sin(bearing) * eastZ + cos(bearing) * northZ
        return doubleArrayOf(
            ce * hx + sin(elevation) * upX,
            ce * hy + sin(elevation) * upY,
            ce * hz + sin(elevation) * upZ,
        )
    }

    /** Yer yuzeyinde, ucaktan [angle] radyan uzakta, [bearing] yonundeki nokta. */
    fun surfaceAt(angle: Double, bearing: Double): DoubleArray {
        val sa = sin(angle)
        val hx = sin(bearing) * eastX + cos(bearing) * northX
        val hy = sin(bearing) * eastY + cos(bearing) * northY
        val hz = sin(bearing) * eastZ + cos(bearing) * northZ
        return doubleArrayOf(
            cos(angle) * upX + sa * hx,
            cos(angle) * upY + sa * hy,
            cos(angle) * upZ + sa * hz,
        )
    }

    /** Bakisin kuzeyden acisi, radyan. */
    val azimuthRad: Double get() = az

    /** Ekrandaki bir noktadan cikan bakis isininin birim vektoru (izdusumun tersi). */
    fun unproject(x: Float, y: Float): DoubleArray {
        val a = (x - cx) / focal.toDouble()
        val b = (cy - y) / focal.toDouble()
        val dx = fwdX + a * rightX + b * camUpX
        val dy = fwdY + a * rightY + b * camUpY
        val dz = fwdZ + a * rightZ + b * camUpZ
        val length = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
        return doubleArrayOf(dx / length, dy / length, dz / length)
    }

    /**
     * Bakis isininin yer yuzeyine dustugu nokta; ufkun otesine bakiyorsa null.
     */
    fun surfaceHit(x: Float, y: Float): DoubleArray? {
        val d = unproject(x, y)
        val b = posX * d[0] + posY * d[1] + posZ * d[2]
        val c = posX * posX + posY * posY + posZ * posZ - 1.0
        val disc = b * b - c
        if (disc < 0.0) return null
        val t = -b - kotlin.math.sqrt(disc)
        if (t <= 0.0) return null
        return doubleArrayOf(posX + t * d[0], posY + t * d[1], posZ + t * d[2])
    }
}
