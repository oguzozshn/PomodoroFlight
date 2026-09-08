package com.oguzozsahin.ucusmodu.timer

import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.FocusRecord
import com.oguzozsahin.ucusmodu.data.LogEntry
import com.oguzozsahin.ucusmodu.data.Persisted
import com.oguzozsahin.ucusmodu.data.Phase
import com.oguzozsahin.ucusmodu.data.Route
import kotlin.math.roundToInt

/** Faz bitiminde duyulacak anons. */
enum class PhaseAlert { LEVEL, LAND, BOARDING }

/**
 * Bir fazin bitisinin sonucu.
 *
 * Saf bir tarif: ses, titresim, bildirim, servis ve dosyaya yazma burada
 * yalnizca *soylenir*, yapilmaz. Yapan taraf [FlightEngine].
 */
data class PhaseOutcome(
    val data: Persisted,
    val phase: Phase,
    val remainingMs: Long,
    /** Otomatik mola: cagiran taraf sayaci hemen baslatmali. */
    val autoStart: Boolean,
    /** Gunluge yazilacak kayit; odak disi fazlarda ve varilmis rotada null. */
    val record: FocusRecord?,
    val alert: PhaseAlert?,
    /** Rota tam bu pomodoroda tamamlandiysa dogru. */
    val arrived: Boolean,
    /** Bu pomodoroda gercekten kat edilen mesafe. */
    val gainedKm: Double,
    /** Varisa kalan pomodoro sayisi; bildirim metni icin. */
    val legsLeft: Int,
)

/**
 * Sayacin faz gecisi. Android'e hic dokunmadigi icin cihaz olmadan
 * sinanabiliyor — uygulamanin en can alici mantigi burasi.
 */
object FlightCore {

    /**
     * [before] fazini bitirir ve sonrasini hesaplar.
     *
     * @param skipped ATLA ile erken gecildi mi. Mesafe ve rota ilerlemesi
     *   normal biten pomodorodakiyle ayni isler; degisen, toplam odak suresine
     *   sayilmamasi ve gunlukte isaretlenmesi.
     */
    fun finishPhase(
        before: FlightUiState,
        data: Persisted,
        now: Long,
        skipped: Boolean = false,
    ): PhaseOutcome {
        val route = before.route

        if (before.phase != Phase.FOCUS) {
            return PhaseOutcome(
                data = data,
                phase = Phase.FOCUS,
                remainingMs = before.durationMsOf(Phase.FOCUS),
                autoStart = false,
                record = null,
                alert = PhaseAlert.BOARDING,
                arrived = false,
                gainedKm = 0.0,
                legsLeft = before.legsLeft,
            )
        }

        // Varilmis rotada kazanilacak bacak kalmadi. Buraya yine de
        // girilebiliyor (molada Atla, sonra tekrar Atla) ve eskiden her
        // seferinde toplam km ile toplam odaga bir bacak ekliyordu:
        // iki dokunusla bedava mesafe. Artik hicbir sey eklemeden doner.
        if (before.arrived) {
            return PhaseOutcome(
                data = data,
                phase = Phase.FOCUS,
                remainingMs = before.durationMsOf(Phase.FOCUS),
                autoStart = false,
                record = null,
                alert = null,
                arrived = false,
                gainedKm = 0.0,
                legsLeft = 0,
            )
        }

        // Gercekte odakta gecen sure. Normal biten pomodoroda ayarlanan
        // surenin tamami, atlanmisda kalanin dusuldugu kisim.
        val focusMs = before.durationMsOf(Phase.FOCUS)
        val focusedMs = (focusMs - before.remainingMs).coerceIn(0L, focusMs)

        // Kazanilan mesafe nominal bacak boyu degil, rotada gercekten
        // ilerlenen kadar. Ikisi normalde ayni (km = bacak x bacak boyu),
        // ama odak suresi rota yarim kalmisken degistirilirse bacak boyu
        // buyur ve son bacak rotanin ucundan tasar. Nominali yazmak o an
        // hic ucumadan mil kazandiriyordu.
        val flownBefore = before.flownOnRoute
        val flown = (flownBefore + route.kmPerLeg).coerceAtMost(route.km)
        val gained = (flown - flownBefore).coerceAtLeast(0.0)

        val progress = data.progress.toMutableMap()
        progress[route.key] = flown
        val arrivedNow = flown >= route.km - FlightUiState.ARRIVAL_EPSILON_KM
        val log = if (arrivedNow) {
            listOf(LogEntry(route.key, route.km.roundToInt(), now)) + data.log
        } else {
            data.log
        }

        val next = data.copy(
            progress = progress,
            log = log,
            // Atlanan pomodoro odak sayilmaz: 25 dakikaligi ucuncu
            // saniyede atlayinca "toplam odak" 25 dakika artmamali.
            totalMs = data.totalMs + if (skipped) 0L else focusMs,
            totalKm = data.totalKm + gained,
            cycle = (data.cycle + 1) % 4,
        )

        val phase = if (next.cycle == 0) Phase.LONG else Phase.BREAK
        return PhaseOutcome(
            data = next,
            phase = phase,
            remainingMs = before.durationMsOf(phase),
            // Varista mola otomatik baslamaz: ucus bitti, ekranda damga var.
            autoStart = next.autoBreak && !arrivedNow,
            record = FocusRecord(
                time = now,
                routeKey = route.key,
                minutes = (focusedMs / 60_000L).toInt(),
                km = gained,
                skipped = skipped,
            ),
            alert = if (arrivedNow) PhaseAlert.LAND else PhaseAlert.LEVEL,
            arrived = arrivedNow,
            gainedKm = gained,
            legsLeft = if (arrivedNow) 0 else Route.legsFor(route.km - flown, data.focusMin),
        )
    }
}
