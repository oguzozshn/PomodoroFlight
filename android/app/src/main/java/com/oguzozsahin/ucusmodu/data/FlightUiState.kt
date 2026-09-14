package com.oguzozsahin.ucusmodu.data

/** Ekranin ve bildirimin tek kaynagi olan degismez durum. */
data class FlightUiState(
    val route: Route,
    val phase: Phase,
    val running: Boolean,
    val remainingMs: Long,
    /** Etkin ucus; baska rotaya aitse bu rotada yol alinmamistir. */
    val flight: ActiveFlight?,
    val log: List<LogEntry>,
    val focusMin: Int,
    val breakMin: Int,
    val longBreakMin: Int = LONG_BREAK_MIN,
    val autoBreak: Boolean,
    val sound: Boolean,
    val passenger: String,
    val cycle: Int,
    val totalMs: Long,
    val totalKm: Double,
) {
    /** Bu rotada kat edilen mesafe. */
    val flownOnRoute: Double
        get() = flight?.takeIf { it.routeKey == route.key }?.flown?.coerceIn(0.0, route.km) ?: 0.0

    /** Tamamlanmis bacak sayisi; gosterim icin mesafeden turetilir. */
    val done: Int
        get() {
            if (route.kmPerLeg <= 0.0) return 0
            return kotlin.math.floor(flownOnRoute / route.kmPerLeg + 1e-9)
                .toInt()
                .coerceIn(0, route.legs)
        }

    val arrived: Boolean get() = flownOnRoute >= route.km - ARRIVAL_EPSILON_KM

    /** Bu rotada yol alinmis mi? Yarim bacak da sayilir, "done > 0" yetmez. */
    val started: Boolean get() = flownOnRoute > 0.0

    val legsLeft: Int get() = (route.legs - done).coerceAtLeast(0)

    /** Bu rotada suren (inmemis) bir ucus var mi. */
    val flightActive: Boolean
        get() = flight?.let { it.routeKey == route.key && !it.landed } ?: false

    /**
     * Baska rotaya gecmek bu ucusu yarida birakir mi? Yol alinmis ve inmemis
     * bir ucus varsa evet; arayuz once sormali.
     */
    fun switchEndsFlight(key: String): Boolean =
        key != route.key && flightActive && flownOnRoute > 0.0 && !arrived

    /**
     * Suren bacagin dolulugu (0..1). Oturum 25 dakikanin kati degilse bacak
     * siniri oturum sonuna denk gelmiyor; ucus planindaki kutu yarim kaliyor.
     */
    val legFill: Float
        get() {
            if (route.kmPerLeg <= 0.0 || arrived) return 0f
            return (route.km * fraction / route.kmPerLeg - done).toFloat().coerceIn(0f, 1f)
        }

    fun durationMsOf(phase: Phase): Long = when (phase) {
        Phase.FOCUS -> focusMin * 60_000L
        Phase.BREAK -> breakMin * 60_000L
        Phase.LONG -> longBreakMin * 60_000L
    }

    val phaseDurationMs: Long get() = durationMsOf(phase)

    /** Rotanin ne kadari uculdu (0..1) — devam eden pomodoro da orantili sayilir. */
    val fraction: Float
        get() {
            var f = if (route.km <= 0.0) 0.0 else flownOnRoute / route.km
            if (phase == Phase.FOCUS && !arrived) {
                val elapsed = 1.0 - remainingMs.toDouble() / durationMsOf(Phase.FOCUS)
                f += elapsed.coerceIn(0.0, 1.0) * (route.kmForFocus(focusMin) / route.km)
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

        /** Kayan noktali toplamada varisin kacmamasi icin birkac metrelik pay. */
        const val ARRIVAL_EPSILON_KM = 0.001
    }
}
