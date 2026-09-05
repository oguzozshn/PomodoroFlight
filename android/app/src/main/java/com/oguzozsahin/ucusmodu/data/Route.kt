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

        fun of(key: String, focusMin: Int = DEFAULT_FOCUS_MIN): Route? {
            val parts = key.split("-")
            if (parts.size != 2) return null
            return of(parts[0], parts[1], focusMin)
        }

        fun of(from: String, to: String, focusMin: Int = DEFAULT_FOCUS_MIN): Route? {
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
                legs = legsFor(km, focusMin),
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

        /**
         * Rotayi bitirmek icin kac pomodoro gerektigi. Pomodoro ne kadar
         * uzunsa o kadar az bacak eder; toplam odak suresi ayni kalir.
         */
        fun legsFor(km: Double, focusMin: Int = DEFAULT_FOCUS_MIN): Int =
            max(1, (km / (KM_PER_MINUTE * focusMin)).roundToInt())

        /**
         * Bacak sayisini kat edilen mesafeye cevirir; eski kayitlarin
         * gocurulmesi icin.
         *
         * Bacak basina mesafe 850 km degil, rotanin kendi [kmPerLeg] degeri:
         * bacak sayisi yuvarlanarak bulundugu icin ikisi ayni degil. IST-JFK
         * 8026 km ve 9 bacak, yani bacak basina 892 km. 850 ile cevirmek
         * kullanicinin ilerlemesini sessizce geri alirdi.
         */
        fun kmForLegs(routeKey: String, legs: Int, focusMin: Int = DEFAULT_FOCUS_MIN): Double {
            if (legs <= 0) return 0.0
            val route = of(routeKey, focusMin) ?: return legs * KM_PER_POMODORO
            return (legs * route.kmPerLeg).coerceAtMost(route.km)
        }
    }
}
