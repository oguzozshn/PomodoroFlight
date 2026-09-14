package com.oguzozsahin.ucusmodu.data

import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Rotanin gercek seyir hiziyla suresi (dakika). Kalkis, taksi ve inis dahil
 * degil; pomodoro olcegiyle ayni tanim, bu yuzden odak suresinden yalniz 25
 * dakikaya yuvarlama kadar ayrilir.
 */
fun cruiseMinutes(km: Double): Int = (km / CRUISE_KMH * 60.0).roundToInt()

/** Rotayi bitirmek icin gereken toplam odak suresi (dakika); oturum uzunlugundan bagimsiz. */
fun focusMinutesFor(legs: Int): Int = legs * POMODORO_MIN

/** 585 -> "9 sa 45 dk", 45 -> "45 dk" */
fun formatDuration(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m dk"
        m == 0 -> "$h sa"
        else -> "$h sa $m dk"
    }
}

/**
 * Rotanin kalanini bitirmek icin gereken takvim suresi: odaklar + aralarindaki molalar.
 *
 * Mola sayisi pomodoro sayisina degil oturum sayisina bagli: 50 dakikalik
 * oturumda iki pomodoro arasina mola girmez. Dorduncu oturumdan sonraki uzun
 * mola da hesaba katilir.
 */
fun remainingScheduleMinutes(
    legsLeft: Int,
    focusMin: Int,
    breakMin: Int,
    cycle: Int,
    longBreakMin: Int = FlightUiState.LONG_BREAK_MIN,
): Int {
    if (legsLeft <= 0) return 0
    val sessions = maxOf(1, ceil(legsLeft * POMODORO_MIN / focusMin.coerceAtLeast(1).toDouble()).toInt())
    var total = legsLeft * POMODORO_MIN
    for (i in 1 until sessions) {
        total += if ((cycle + i) % 4 == 0) longBreakMin else breakMin
    }
    return total
}

/** Rotaya sabitlenmis, her acilista ayni cikan kapi numarasi. */
fun gateOf(routeKey: String): String {
    var n = 0
    for (ch in routeKey) n = (n * 31 + ch.code) and 0x7FFFFFFF
    val letter = "ABCD"[n % 4]
    return "$letter${1 + n / 4 % 30}"
}

/** Dunyanin ekvator cevresi; toplam yolun kac tur ettigini olcmek icin. */
const val EARTH_CIRCUMFERENCE_KM = 40_075.0

/** Toplam kat edilen yolun kac dunya turu ettigi. */
fun worldLaps(km: Double): Double = km / EARTH_CIRCUMFERENCE_KM

/** Suren turun tamamlanma orani (0..1). */
fun currentLapFraction(km: Double): Float {
    if (km <= 0.0) return 0f
    val laps = worldLaps(km)
    return (laps - kotlin.math.floor(laps)).toFloat()
}
