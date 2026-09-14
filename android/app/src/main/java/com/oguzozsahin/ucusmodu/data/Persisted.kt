package com.oguzozsahin.ucusmodu.data

/**
 * Uygulamanin kalici durumu. Web surumundeki localStorage kaydinin birebir karsiligi,
 * ustune sureci hayatta kalan sayac alanlari (running/endAt) eklendi.
 */
data class Persisted(
    val routeKey: String = "IST-JFK",
    /** Etkin ucus; ayni anda tek bir tane (bkz. [Flights]). */
    val flight: ActiveFlight? = null,
    val log: List<LogEntry> = emptyList(),
    val focusMin: Int = 25,
    val breakMin: Int = 5,
    /** Her dort oturumda bir gelen uzun mola (dakika). */
    val longBreakMin: Int = 15,
    val autoBreak: Boolean = true,
    val sound: Boolean = true,
    val passenger: String = "",
    val cycle: Int = 0,
    val totalMs: Long = 0L,
    val totalKm: Double = 0.0,
    val phase: Phase = Phase.FOCUS,
    val running: Boolean = false,
    val endAt: Long = 0L,
    val remainingMs: Long = 25 * 60_000L,
)
