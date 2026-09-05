package com.oguzozsahin.ucusmodu.data

import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Bir pomodoro boyunca kat edilen seyir mesafesi. */
const val KM_PER_POMODORO = 850.0

private const val EARTH_R = 6371.0

data class Vec3(val x: Double, val y: Double, val z: Double)

data class LatLon(val lat: Double, val lon: Double)

/** Cografi koordinati birim kure uzerindeki vektore cevirir. */
fun vecOf(lat: Double, lon: Double): Vec3 {
    val p = Math.toRadians(lat)
    val l = Math.toRadians(lon)
    return Vec3(cos(p) * cos(l), cos(p) * sin(l), sin(p))
}

/** Birim vektoru tekrar enlem/boylama cevirir. */
fun latLonOf(v: Vec3): LatLon =
    LatLon(Math.toDegrees(asin(v.z.coerceIn(-1.0, 1.0))), Math.toDegrees(atan2(v.y, v.x)))

/** Kure uzerinde iki nokta arasinda buyuk daire uzerinden enterpolasyon. */
fun slerp(a: Vec3, b: Vec3, t: Double): Vec3 {
    val d = (a.x * b.x + a.y * b.y + a.z * b.z).coerceIn(-1.0, 1.0)
    val w = acos(d)
    if (w < 1e-8) return a
    val s = sin(w)
    val k1 = sin((1 - t) * w) / s
    val k2 = sin(t * w) / s
    return Vec3(a.x * k1 + b.x * k2, a.y * k1 + b.y * k2, a.z * k1 + b.z * k2)
}

/** Iki havalimani arasindaki buyuk daire mesafesi (km). */
fun haversine(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val dLat = Math.toRadians(bLat - aLat)
    val dLon = Math.toRadians(bLon - aLon)
    val h = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_R * asin(sqrt(h).coerceIn(0.0, 1.0))
}

/** Bir noktadan digerine bakarken kuzeyden olculen ilk yon acisi (derece, 0..360). */
fun bearing(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
    val p1 = Math.toRadians(aLat)
    val p2 = Math.toRadians(bLat)
    val dLon = Math.toRadians(bLon - aLon)
    val y = sin(dLon) * cos(p2)
    val x = cos(p1) * sin(p2) - sin(p1) * cos(p2) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
}
