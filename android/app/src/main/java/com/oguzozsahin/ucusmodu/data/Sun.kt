package com.oguzozsahin.ucusmodu.data

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gunesin o an dunyanin neresinde tam tepede oldugunu hesaplar; kurenin
 * gunduz/gece sinirini cizmek icin yeter (dusuk hassasiyetli, ~0.01° mertebesi).
 */
object Sun {

    /** 1 Ocak 2000, 12:00 UTC — astronomik hesaplarin baslangic ani. */
    private const val J2000_MILLIS = 946_728_000_000L
    private const val DAY_MILLIS = 86_400_000.0

    /** Gunesin tam tepede oldugu nokta. */
    fun subsolarPoint(timeMillis: Long): LatLon {
        val days = (timeMillis - J2000_MILLIS) / DAY_MILLIS

        // ortalama boylam ve ortalama anomali
        val meanLongitude = Math.toRadians((280.460 + 0.9856474 * days) % 360.0)
        val meanAnomaly = Math.toRadians((357.528 + 0.9856003 * days) % 360.0)

        // ekliptik boylam: merkez denklemi ile duzeltilmis
        val eclipticLongitude = meanLongitude +
            Math.toRadians(1.915) * sin(meanAnomaly) +
            Math.toRadians(0.020) * sin(2 * meanAnomaly)
        val obliquity = Math.toRadians(23.439 - 0.0000004 * days)

        val declination = asin(sin(obliquity) * sin(eclipticLongitude))
        val rightAscension = atan2(cos(obliquity) * sin(eclipticLongitude), cos(eclipticLongitude))

        // Greenwich yildiz zamani: dunyanin o andaki donme acisi
        val siderealTime = Math.toRadians((280.46061837 + 360.98564736629 * days) % 360.0)
        val longitude = Math.toDegrees(rightAscension - siderealTime)

        return LatLon(Math.toDegrees(declination), normalizeLongitude(longitude))
    }

    private fun normalizeLongitude(value: Double): Double {
        var v = value % 360.0
        if (v > 180.0) v -= 360.0
        if (v < -180.0) v += 360.0
        return v
    }
}
