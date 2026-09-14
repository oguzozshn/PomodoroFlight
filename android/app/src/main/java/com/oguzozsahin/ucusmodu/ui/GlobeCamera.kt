package com.oguzozsahin.ucusmodu.ui

import com.oguzozsahin.ucusmodu.data.LatLon
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Ortografik kure kamerasi: nereye baktigi, ekranda nerede durdugu ve yaricapi.
 *
 * Bakis yonunden bir taban turetilir (dogu, kuzey, yukari); bir noktayi
 * izdusurmek boylece trigonometri degil yalnizca uc ic carpim eder ve arkada
 * kalanlar tek karsilastirmayla elenir.
 *
 * Compose'a hic dokunmuyor: cizim [GlobeMap] icinde, matematik burada.
 * Ayrildigi icin cihaz olmadan sinanabiliyor.
 */
internal class GlobeCamera(
    val centerLat: Double,
    val centerLon: Double,
    val cx: Float,
    val cy: Float,
    val r: Float,
) {
    private val phi = Math.toRadians(centerLat)
    private val lambda = Math.toRadians(centerLon)

    val eastX = (-sin(lambda)).toFloat()
    val eastY = cos(lambda).toFloat()
    val eastZ = 0f

    val northX = (-sin(phi) * cos(lambda)).toFloat()
    val northY = (-sin(phi) * sin(lambda)).toFloat()
    val northZ = cos(phi).toFloat()

    val upX = (cos(phi) * cos(lambda)).toFloat()
    val upY = (cos(phi) * sin(lambda)).toFloat()
    val upZ = sin(phi).toFloat()

    fun screenX(x: Float, y: Float, z: Float) = cx + r * (x * eastX + y * eastY + z * eastZ)
    fun screenY(x: Float, y: Float, z: Float) = cy - r * (x * northX + y * northY + z * northZ)
    fun visible(x: Float, y: Float, z: Float) = x * upX + y * upY + z * upZ >= 0f

    fun at(point: LatLon): ProjectedPoint {
        val p = Math.toRadians(point.lat)
        val l = Math.toRadians(point.lon)
        val x = (cos(p) * cos(l)).toFloat()
        val y = (cos(p) * sin(l)).toFloat()
        val z = sin(p).toFloat()
        return ProjectedPoint(screenX(x, y, z), screenY(x, y, z), visible(x, y, z))
    }

    /**
     * Ekrandaki noktanin kure uzerindeki karsiligi; kurenin disiysa null.
     * [radiusScale] yakinlastirmayi cizimden once denemek icin kullanilir.
     */
    fun locate(x: Float, y: Float, radiusScale: Float = 1f): LatLon? {
        val radius = r * radiusScale
        if (radius <= 0f) return null
        val dx = (x - cx) / radius
        val dy = (cy - y) / radius
        val rho = sqrt(dx * dx + dy * dy)
        if (rho > 1f) return null
        if (rho < 1e-4f) return LatLon(centerLat, centerLon)

        val c = asin(rho.toDouble().coerceIn(-1.0, 1.0))
        val phi0 = Math.toRadians(centerLat)
        val lat = asin(
            (cos(c) * sin(phi0) + dy * sin(c) * cos(phi0) / rho).coerceIn(-1.0, 1.0),
        )
        val lon = Math.toRadians(centerLon) + atan2(
            dx * sin(c),
            rho * cos(c) * cos(phi0) - dy * sin(c) * sin(phi0),
        )
        return LatLon(Math.toDegrees(lat), Math.toDegrees(lon))
    }

    /**
     * [anchor] koordinatini, olcek [radiusScale] katina ciktiginda hala (x, y)
     * pikselinde tutan kamera merkezini cozer. Boyle bir kamera yoksa null.
     *
     * Yaklasim yapilmiyor: nokta yeni kamera tabaninda (dogu, kuzey, yukari)
     * = (a, d, s) bilesenlerine sahip olmali. Kuzey ile yukari bileseni
     * kameranin enlemi etrafinda bir donmedir, o yuzden enlem tek bir sinus
     * denkleminden cikar; boylam da yatay bilesenin donme acisindan.
     *
     * Sinus denkleminin iki koku var ve ikisi de ayni z bilesenini veriyor.
     * Yalnizca birincisi alininca kamera bazen sabitlenen noktayi kurenin
     * arka yuzune atiyordu: parmagin altindaki sehir cimdiklerken oteki
     * tarafa gecip kayboluyordu. Artik iki kok de denenip sonuc geri
     * izdusurulerek dogrulaniyor.
     */
    fun solveCamera(anchor: LatLon, x: Float, y: Float, radiusScale: Float): LatLon? {
        val radius = r * radiusScale
        if (radius <= 0f) return null
        val a = ((x - cx) / radius).toDouble()
        val d = ((cy - y) / radius).toDouble()
        val rho2 = a * a + d * d
        if (rho2 > 1.0) return null
        val s = sqrt(1.0 - rho2)

        val p = Math.toRadians(anchor.lat)
        val l = Math.toRadians(anchor.lon)
        val px = cos(p) * cos(l)
        val py = cos(p) * sin(l)
        val pz = sin(p)

        // |pz| <= rr olmali. Esitlik tam kutubu sabitlerken cikiyor ve
        // yuvarlama yuzunden rr 1'in bir ulp altinda kaliyordu: sinir
        // durumda cozum yok sanilip merkeze gore yakinlastiriliyordu.
        val rr = sqrt(s * s + d * d)
        if (rr < 1e-9 || abs(pz) > rr + 1e-9) return null

        val tilt = atan2(d, s)
        val root = asin((pz / rr).coerceIn(-1.0, 1.0))
        for (phi0 in doubleArrayOf(root - tilt, PI - root - tilt)) {
            val lat = Math.toDegrees(wrapAngle(phi0))
            // Kamera enlemi kutbu asamaz; asan kok bu kurulusta temsil
            // edilemiyor (kure ters donerdi), atlanir.
            if (abs(lat) > 90.0) continue

            val b = s * cos(phi0) - d * sin(phi0)
            // a ve b'nin ikisi de sifirsa sabitlenen nokta tam kutupta:
            // boylam serbest, kamera bulundugu boylamda kalir.
            val lon = if (abs(a) < 1e-12 && abs(b) < 1e-12) {
                centerLon
            } else {
                Math.toDegrees(atan2(py, px) - atan2(a, b))
            }

            if (holds(anchor, lat, lon, radius, x, y)) return LatLon(lat, lon)
        }
        return null
    }

    /** Cozulen kamera gercekten [anchor]'i (x, y) pikselinde tutuyor mu. */
    private fun holds(
        anchor: LatLon,
        lat: Double,
        lon: Double,
        radius: Float,
        x: Float,
        y: Float,
    ): Boolean {
        val point = GlobeCamera(lat, lon, cx, cy, radius).at(anchor)
        return point.visible && abs(point.x - x) < 0.5f && abs(point.y - y) < 0.5f
    }
}

/** Aciyi (-pi, pi] araligina indirger. */
private fun wrapAngle(radians: Double): Double {
    var v = radians
    while (v > PI) v -= 2 * PI
    while (v <= -PI) v += 2 * PI
    return v
}

internal class ProjectedPoint(val x: Float, val y: Float, val visible: Boolean)
