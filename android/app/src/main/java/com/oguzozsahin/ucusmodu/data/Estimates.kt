package com.oguzozsahin.ucusmodu.data

import kotlin.math.roundToInt

/** Yolcu ucaginin seyir hizi; ucus suresi tahmininde kullanilir. */
private const val CRUISE_KMH = 850.0

/** Manevra, taksi ve tirmanis icin sabit pay (dakika). */
private const val GROUND_MINUTES = 30

/** Rotanin gercek hayattaki kaba ucus suresi (dakika). */
fun realFlightMinutes(km: Double): Int = (km / CRUISE_KMH * 60.0).roundToInt() + GROUND_MINUTES

/** Rotayi bitirmek icin gereken toplam odak suresi (dakika). */
fun focusMinutesFor(legs: Int, focusMin: Int): Int = legs * focusMin

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
 * Dorduncu pomodorodan sonraki uzun mola da hesaba katilir.
 */
fun remainingScheduleMinutes(
    legsLeft: Int,
    focusMin: Int,
    breakMin: Int,
    cycle: Int,
): Int {
    if (legsLeft <= 0) return 0
    var total = legsLeft * focusMin
    for (i in 1 until legsLeft) {
        total += if ((cycle + i) % 4 == 0) FlightUiState.LONG_BREAK_MIN else breakMin
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
