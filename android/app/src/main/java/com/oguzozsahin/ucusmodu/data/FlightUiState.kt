package com.oguzozsahin.ucusmodu.data

/** Ekranin ve bildirimin tek kaynagi olan degismez durum. */
data class FlightUiState(
    val route: Route,
    val phase: Phase,
    val running: Boolean,
    val remainingMs: Long,
    val progress: Map<String, Int>,
    val log: List<LogEntry>,
    val focusMin: Int,
    val breakMin: Int,
    val autoBreak: Boolean,
    val autoStart: Boolean,
    val sound: Boolean,
    val cycle: Int,
    val totalMs: Long,
    val totalKm: Double,
) {
    /** Bu rotada tamamlanmis bacak sayisi. */
    val done: Int get() = (progress[route.key] ?: 0).coerceIn(0, route.legs)

    val arrived: Boolean get() = done >= route.legs

    val legsLeft: Int get() = (route.legs - done).coerceAtLeast(0)

    fun durationMsOf(phase: Phase): Long = when (phase) {
        Phase.FOCUS -> focusMin * 60_000L
        Phase.BREAK -> breakMin * 60_000L
        Phase.LONG -> LONG_BREAK_MIN * 60_000L
    }

    val phaseDurationMs: Long get() = durationMsOf(phase)

    /** Rotanin ne kadari uculdu (0..1) — devam eden pomodoro da orantili sayilir. */
    val fraction: Float
        get() {
            var f = done.toDouble() / route.legs
            if (phase == Phase.FOCUS && !arrived) {
                val elapsed = 1.0 - remainingMs.toDouble() / durationMsOf(Phase.FOCUS)
                f += elapsed.coerceIn(0.0, 1.0) / route.legs
            }
            return f.coerceIn(0.0, 1.0).toFloat()
        }

    val flownKm: Double get() = route.km * fraction

    /** Ekrandaki irtifa gostergesi: kalkista tirmanir, inise dogru alcalir. */
    val altitudeM: Int
        get() {
            val f = fraction
            if (arrived || f == 0f) return 0
            val climb = (f / 0.06f).coerceAtMost(1f)
            val descend = ((1f - f) / 0.06f).coerceAtMost(1f)
            return (11300 * minOf(climb, descend) / 100).toInt() * 100
        }

    companion object {
        const val LONG_BREAK_MIN = 15
    }
}
