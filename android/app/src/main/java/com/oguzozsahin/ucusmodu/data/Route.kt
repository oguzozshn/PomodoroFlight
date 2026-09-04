package com.oguzozsahin.ucusmodu.data

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Iki havalimani arasindaki buyuk daire rotasi ve bunun pomodoro cinsinden karsiligi.
 */
data class Route(
    val key: String,
    val origin: Airport,
    val dest: Airport,
    val km: Double,
    val legs: Int,
    val a: Vec3,
    val b: Vec3,
    val center: LatLon,
    val flightNo: String,
) {
    /** Tek bir pomodoronun tasidigi mesafe. */
    val kmPerLeg: Double get() = km / legs

    /** Rota uzerinde t oranindaki nokta. */
    fun pointAt(t: Double): LatLon = latLonOf(slerp(a, b, t))

    companion object {

        fun of(key: String): Route? {
            val parts = key.split("-")
            if (parts.size != 2) return null
            return of(parts[0], parts[1])
        }

        fun of(from: String, to: String): Route? {
            val o = PlaceRegistry.find(from) ?: return null
            val d = PlaceRegistry.find(to) ?: return null
            if (o.code == d.code) return null
            val km = haversine(o.lat, o.lon, d.lat, d.lon)
            val a = vecOf(o.lat, o.lon)
            val b = vecOf(d.lat, d.lon)
            return Route(
                key = "${o.code}-${d.code}",
                origin = o,
                dest = d,
                km = km,
                legs = max(1, (km / KM_PER_POMODORO).roundToInt()),
                a = a,
                b = b,
                center = latLonOf(slerp(a, b, 0.5)),
                flightNo = flightNumber("${o.code}-${d.code}"),
            )
        }

        /** Web surumuyle ayni ucus numarasi uretimi: rotaya sabitlenmis TKxxxx. */
        private fun flightNumber(key: String): String {
            var n = 0
            for (ch in key) n = (n * 31 + ch.code) % 9000
            return "TK${1000 + n}"
        }

        /** Rota gerektirmeden sadece mesafe/bacak sayisi hesaplar (liste gorunumleri icin). */
        fun legsFor(km: Double): Int = max(1, (km / KM_PER_POMODORO).roundToInt())
    }
}
