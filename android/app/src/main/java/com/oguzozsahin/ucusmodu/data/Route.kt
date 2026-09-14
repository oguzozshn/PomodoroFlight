package com.oguzozsahin.ucusmodu.data

import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Iki havalimani arasindaki buyuk daire rotasi ve bunun pomodoro cinsinden karsiligi.
 *
 * Olcek ayardan bagimsiz: bir pomodoro 25 dakika ve gercek seyir hiziyla ~354 km,
 * dolayisiyla bir rotanin kac pomodoro ettigi odak suresi ayariyla oynamaz. Ayar
 * yalniz bir oturumun kac pomodoro tasidigini belirler: 50 dakika iki, 37 dakika 1,48.
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
    /** Bir pomodoronun (bir bacagin) tasidigi mesafe. */
    val kmPerLeg: Double get() = km / legs

    /**
     * Bir odak oturumunun bu rotada kazandirdigi mesafe.
     *
     * Bacak boyu nominal [KM_PER_POMODORO] degil rotanin kendi [kmPerLeg] degeri:
     * bacak sayisi yuvarlanarak bulunuyor (IST-JFK 8026 km, 23 bacak, 349 km/bacak).
     * Oturum bunun focusMin / 25 katini tasir; boylece bacak sayisi kadar tam
     * pomodoro rotayi tastamam bitirir.
     */
    fun kmForFocus(focusMin: Int): Double = kmPerLeg * focusMin / POMODORO_MIN

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
                legs = legsFor(km),
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

        /** Rotayi bitirmek icin kac pomodoro gerektigi; odak suresi ayarina bakmaz. */
        fun legsFor(km: Double): Int = max(1, (km / KM_PER_POMODORO).roundToInt())

        /**
         * Bacak sayisini kat edilen mesafeye cevirir; eski kayitlarin
         * gocurulmesi icin.
         *
         * Bacak basina mesafe nominal [KM_PER_POMODORO] degil, rotanin kendi
         * [kmPerLeg] degeri: bacak sayisi yuvarlanarak bulundugu icin ikisi ayni
         * degil. Nominal degerle cevirmek ilerlemeyi sessizce kaydirirdi.
         */
        fun kmForLegs(routeKey: String, legs: Int): Double {
            if (legs <= 0) return 0.0
            val route = of(routeKey) ?: return legs * KM_PER_POMODORO
            return (legs * route.kmPerLeg).coerceAtMost(route.km)
        }
    }
}
