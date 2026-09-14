package com.oguzozsahin.ucusmodu.data

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Etkin ucus. Ayni anda tek bir tane var.
 *
 * Eskiden ilerleme rota basina bir km sayacindaydi: bir rotadan cikip donunce
 * ucak kaldigi yerden devam ediyor, yarida birakilan ucus hicbir listede
 * gorunmuyordu. Artik yolculuk bilet koparilinca baslar, varisla ya da
 * "Ucusu bitir" ile kapanir ve ne kadari uculduysa onunla gunluge duser.
 */
data class ActiveFlight(
    val routeKey: String,
    /** Kat edilen km. */
    val flown: Double = 0.0,
    /** Biletin koparildigi an; eski kayittan donusende bilinmiyor (0). */
    val start: Long = 0L,
    /**
     * Varis ani. Satir gunluge yazildi ama ekran "Inis yapildi" durumunu
     * gosterebilsin diye ucus yeni biri baslayana kadar duruyor.
     */
    val landedAt: Long = 0L,
) {
    val landed: Boolean get() = landedAt > 0L
}

/** Ucus kaydinin kurallari. Android'e dokunmadigi icin cihazsiz sinaniyor. */
object Flights {

    /** Yeni, sifirdan bir ucus. Varsa oncekinin yerini alir. */
    fun start(data: Persisted, routeKey: String, now: Long): Persisted =
        data.copy(flight = ActiveFlight(routeKey = routeKey, start = now))

    /**
     * Etkin ucusu kapatir.
     *  - [done]: varis. Satir yazilir, ucus inmis olarak ekranda kalir.
     *  - yarida birakma: uculan yol varsa satir yazilir, ucus silinir; sifir
     *    km'lik ucus (bilet koparilip hic oturum bitmeden) iz birakmaz.
     */
    fun end(data: Persisted, route: Route, done: Boolean, now: Long): Persisted {
        val flight = data.flight ?: return data
        if (flight.routeKey != route.key || flight.landed) return data
        val start = if (flight.start > 0L) flight.start else now

        if (done) {
            val row = LogEntry(route.key, route.km.roundToInt(), now, flown = route.km, start = start, done = true)
            return data.copy(log = listOf(row) + data.log, flight = flight.copy(flown = route.km, landedAt = now))
        }
        if (flight.flown <= 0.0) return data.copy(flight = null)
        val row = LogEntry(route.key, route.km.roundToInt(), now, flown = round1(flight.flown), start = start, done = false)
        return data.copy(log = listOf(row) + data.log, flight = null)
    }

    fun completedCount(log: List<LogEntry>): Int = log.count { it.done }

    fun partialCount(log: List<LogEntry>): Int = log.count { !it.done }

    /** Satirin rota uzunlugu; dönüşümden gelen satirda bilinmiyorsa rotadan. */
    fun kmOf(entry: LogEntry): Double =
        if (entry.km > 0) entry.km.toDouble() else Route.of(entry.routeKey)?.km ?: 0.0

    /** Ucusun ne kadari uculdu: "2 / 23 pomodoro (698 km)". Onay metinleri icin. */
    fun summary(route: Route, flown: Double): String =
        "${formatPomodoros(flown / route.kmPerLeg)} / ${route.legs} pomodoro (${formatNumber(flown)} km)"

    /**
     * Gunluk satirinin ozeti.
     *   tamamlanan: "✓ 23 pom · 8.026 km"
     *   yarim:      "%30 · 7 / 23 pom · 2.444 km"
     */
    fun describe(entry: LogEntry): String {
        val route = Route.of(entry.routeKey)
        val km = kmOf(entry).takeIf { it > 0.0 } ?: entry.flown
        if (entry.done) {
            return "✓ " + (route?.let { "${it.legs} pom · " } ?: "") + "${formatNumber(km)} km"
        }
        val pct = if (km > 0.0) floor(100 * entry.flown / km).toInt() else 0
        val poms = route?.let { "${formatPomodoros(entry.flown / it.kmPerLeg)} / ${it.legs} pom · " } ?: ""
        return "%$pct · $poms${formatNumber(entry.flown)} km"
    }

    /**
     * Kuredeki izler: rota -> ne kadari cizilecek (0..1). Tamamlanan ucus tam
     * yay, yarim birakilan yalniz uculan kisim; ayni rota birden cok kez
     * uculduysa en uzunu.
     */
    fun trails(log: List<LogEntry>): List<Pair<String, Float>> {
        val extent = LinkedHashMap<String, Float>()
        for (entry in log) {
            val km = kmOf(entry)
            val t = when {
                entry.done -> 1f
                km <= 0.0 -> 0f
                else -> (entry.flown / km).toFloat().coerceIn(0f, 1f)
            }
            if (t <= 0f) continue
            if ((extent[entry.routeKey] ?: 0f) < t) extent[entry.routeKey] = t
        }
        return extent.map { it.key to it.value }
    }

    /**
     * Donusum (bir kez): rota basina ilerlemeyi tek etkin ucusa cevirir.
     *  - acik rotanin ilerlemesi etkin ucus olur;
     *  - diger rotalardaki yarim ilerlemeler gunluge yarim satir olarak eklenir;
     *  - gunlukte varisi zaten yazilmis tam ilerlemeler atlanir, cift satir olmaz.
     */
    fun fromProgress(
        progress: Map<String, Double>,
        openRoute: String,
        log: List<LogEntry>,
        now: Long,
    ): Pair<ActiveFlight?, List<LogEntry>> {
        val landedKm = HashMap<String, Int>()
        for (entry in log) if (entry.done && entry.km > 0) landedKm[entry.routeKey] = entry.km

        var flight: ActiveFlight? = null
        val rows = log.toMutableList()
        for ((key, flown) in progress) {
            if (flown <= 0.0) continue
            val alreadyLanded = landedKm[key]?.let { abs(it - flown) <= 1.0 } ?: false
            if (alreadyLanded) continue
            if (key == openRoute) {
                flight = ActiveFlight(routeKey = key, flown = flown)
            } else {
                val km = Route.of(key)?.km?.roundToInt() ?: 0
                rows += LogEntry(key, km, now, flown = round1(flown), start = now, done = false)
            }
        }
        return flight to rows
    }

    private fun round1(x: Double): Double = (x * 10).roundToInt() / 10.0
}
