package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.ActiveFlight
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.Persisted
import com.oguzozsahin.ucusmodu.data.Phase
import com.oguzozsahin.ucusmodu.data.Route
import com.oguzozsahin.ucusmodu.timer.FlightCore
import com.oguzozsahin.ucusmodu.timer.PhaseAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sayacin faz gecisi. Uygulamanin en can alici mantigi burasiydi ve Android'e
 * bagli oldugu icin hic sinanmiyordu; [FlightCore] ayrildiktan sonra cihazsiz
 * kosuyor.
 */
class FlightCoreTest {

    private val now = 1_757_030_400_000L

    private fun route(key: String = "IST-JFK"): Route = Route.of(key)!!

    private fun state(
        route: Route,
        data: Persisted,
        phase: Phase = Phase.FOCUS,
        remainingMs: Long = 0L,
    ) = FlightUiState(
        route = route,
        phase = phase,
        running = false,
        remainingMs = remainingMs,
        flight = data.flight,
        log = data.log,
        focusMin = data.focusMin,
        breakMin = data.breakMin,
        autoBreak = data.autoBreak,
        sound = data.sound,
        passenger = data.passenger,
        cycle = data.cycle,
        totalMs = data.totalMs,
        totalKm = data.totalKm,
    )

    // ---------------- normal donus ----------------

    @Test
    fun `tamamlanan pomodoro bir bacak ilerletir ve molaya gecer`() {
        val r = route()
        val data = Persisted(routeKey = r.key)
        val out = FlightCore.finishPhase(state(r, data), data, now, skipped = false)

        assertEquals(r.kmPerLeg, out.data.flight!!.flown, 1e-9)
        assertEquals(r.kmPerLeg, out.gainedKm, 1e-9)
        assertEquals(r.kmPerLeg, out.data.totalKm, 1e-9)
        assertEquals(25 * 60_000L, out.data.totalMs)
        assertEquals(Phase.BREAK, out.phase)
        assertEquals(5 * 60_000L, out.remainingMs)
        assertEquals(PhaseAlert.LEVEL, out.alert)
        assertFalse(out.arrived)
        assertTrue(out.autoStart)
    }

    @Test
    fun `dorduncu pomodorodan sonra uzun mola gelir`() {
        val r = route()
        var data = Persisted(routeKey = r.key)
        val phases = mutableListOf<Phase>()
        repeat(4) {
            val out = FlightCore.finishPhase(state(r, data), data, now)
            phases += out.phase
            data = out.data
        }
        assertEquals(listOf(Phase.BREAK, Phase.BREAK, Phase.BREAK, Phase.LONG), phases)
        assertEquals(0, data.cycle)
    }

    @Test
    fun `uzun molanin suresi on bes dakika`() {
        val r = route()
        val data = Persisted(routeKey = r.key, cycle = 3)
        val out = FlightCore.finishPhase(state(r, data), data, now)
        assertEquals(Phase.LONG, out.phase)
        assertEquals(FlightUiState.LONG_BREAK_MIN * 60_000L, out.remainingMs)
    }

    @Test
    fun `mola bitince odaga donulur ve gunluge yazilmaz`() {
        val r = route()
        val data = Persisted(routeKey = r.key, cycle = 1)
        val out = FlightCore.finishPhase(state(r, data, phase = Phase.BREAK), data, now)

        assertEquals(Phase.FOCUS, out.phase)
        assertEquals(25 * 60_000L, out.remainingMs)
        assertNull(out.record)
        assertEquals(PhaseAlert.BOARDING, out.alert)
        assertFalse(out.autoStart)
        assertSame(data, out.data)
    }

    // ---------------- varis ----------------

    @Test
    fun `son bacak varisi damgalar ve molayi kendiliginden baslatmaz`() {
        val r = route()
        var data = Persisted(routeKey = r.key)
        repeat(r.legs) { data = FlightCore.finishPhase(state(r, data), data, now).data }

        val last = FlightCore.finishPhase(
            state(r, Persisted(routeKey = r.key, flight = ActiveFlight(r.key, r.km - r.kmPerLeg))),
            Persisted(routeKey = r.key, flight = ActiveFlight(r.key, r.km - r.kmPerLeg)),
            now,
        )
        assertTrue(last.arrived)
        assertEquals(PhaseAlert.LAND, last.alert)
        assertFalse("varista mola kendiliginden baslamaz", last.autoStart)
        assertEquals(1, last.data.log.size)
        assertEquals(r.key, last.data.log[0].routeKey)
        assertEquals(0, last.legsLeft)
        assertEquals(r.km, data.flight!!.flown, 1e-6)
    }

    @Test
    fun `varilmis rotada atlamak bedava mesafe vermez`() {
        val r = route()
        val data = Persisted(
            routeKey = r.key,
            flight = ActiveFlight(r.key, r.km),
            totalKm = r.km,
            totalMs = 9 * 25 * 60_000L,
        )
        val out = FlightCore.finishPhase(state(r, data), data, now, skipped = true)

        assertSame("hicbir sey eklenmemeli", data, out.data)
        assertEquals(0.0, out.gainedKm, 0.0)
        assertNull(out.record)
        assertNull(out.alert)
        assertEquals(Phase.FOCUS, out.phase)
    }

    // ---------------- atlama ----------------

    @Test
    fun `atlanan pomodoro toplam odaga sayilmaz ama mesafeyi verir`() {
        val r = route()
        val data = Persisted(routeKey = r.key)
        // 25 dakikaliktan 3 saniye sonra atlandi.
        val before = state(r, data, remainingMs = 25 * 60_000L - 3_000L)
        val out = FlightCore.finishPhase(before, data, now, skipped = true)

        assertEquals("toplam odak artmamali", 0L, out.data.totalMs)
        assertEquals(r.kmPerLeg, out.gainedKm, 1e-9)
        assertNotNull(out.record)
        assertTrue(out.record!!.skipped)
        assertEquals("uc saniye sifir dakika eder", 0, out.record!!.minutes)
    }

    @Test
    fun `gunluk kaydi gercekte odakta gecen dakikayi yazar`() {
        val r = route()
        val data = Persisted(routeKey = r.key)
        val before = state(r, data, remainingMs = 25 * 60_000L - 12 * 60_000L - 30_000L)
        val out = FlightCore.finishPhase(before, data, now, skipped = true)

        assertEquals(12, out.record!!.minutes)
        assertEquals(now, out.record!!.time)
        assertEquals(r.key, out.record!!.routeKey)
    }

    // ---------------- odak suresi ----------------

    @Test
    fun `50 dakikalik oturum iki bacak ilerletir`() {
        val r = route()
        val data = Persisted(routeKey = r.key, focusMin = 50)
        val out = FlightCore.finishPhase(state(r, data), data, now)

        assertEquals(2 * r.kmPerLeg, out.gainedKm, 1e-9)
        assertEquals(2 * r.kmPerLeg, out.data.flight!!.flown, 1e-9)
        assertEquals(50 * 60_000L, out.data.totalMs)
        assertEquals(r.legs - 2, out.legsLeft)
    }

    @Test
    fun `37 dakikalik oturum bir bacaktan fazla ilerletir, kalan yukari yuvarlanir`() {
        val r = route()
        val data = Persisted(routeKey = r.key, focusMin = 37)
        val out = FlightCore.finishPhase(state(r, data), data, now)

        assertEquals(1.48 * r.kmPerLeg, out.gainedKm, 1e-9)
        // 21,52 bacak kaldi: bildirimde 22 pomodoro yazmali
        assertEquals(r.legs - 1, out.legsLeft)
    }

    @Test
    fun `uzun oturum rotanin ucundan tasan mesafeyi kazandirmaz`() {
        // Son bacak kalmisken 50 dakikalik oturum iki bacak tasir; yalniz
        // gercekten kalan kadar yazilir.
        val r = route()
        val flown = (r.legs - 1) * r.kmPerLeg
        val data = Persisted(
            routeKey = r.key,
            focusMin = 50,
            flight = ActiveFlight(r.key, flown),
            totalKm = flown,
        )
        val out = FlightCore.finishPhase(state(r, data), data, now)

        val actual = r.km - flown
        assertEquals("yalnizca gercekten uculdugu kadar", actual, out.gainedKm, 1e-9)
        assertEquals(r.km, out.data.totalKm, 1e-9)
        assertEquals(actual, out.record!!.km, 1e-9)
        assertTrue(out.arrived)
    }

    // ---------------- ayarlar ----------------

    @Test
    fun `otomatik mola kapaliyken sayac kendiliginden baslamaz`() {
        val r = route()
        val data = Persisted(routeKey = r.key, autoBreak = false)
        val out = FlightCore.finishPhase(state(r, data), data, now)
        assertFalse(out.autoStart)
        assertEquals(Phase.BREAK, out.phase)
    }

    @Test
    fun `mola suresi ayardan gelir`() {
        val r = route()
        val data = Persisted(routeKey = r.key, breakMin = 9)
        val out = FlightCore.finishPhase(state(r, data), data, now)
        assertEquals(9 * 60_000L, out.remainingMs)
    }

    @Test
    fun `kalan pomodoro sayisi bildirim icin dogru hesaplanir`() {
        val r = route()
        val data = Persisted(routeKey = r.key)
        val out = FlightCore.finishPhase(state(r, data), data, now)
        assertEquals(r.legs - 1, out.legsLeft)
    }

    // ---------------- zaman damgasi ----------------

    @Test
    fun `kayit fazin bittigi ana tarihlenir, fark edildigi ana degil`() {
        // Ekran kapaliyken biten faz saatler sonra fark edilebiliyor; o
        // pomodoroyu bugune yazmak gun dagilimini yanlis yerden doldururdu.
        val r = route()
        val data = Persisted(routeKey = r.key)
        val endedAt = now - 9 * 60 * 60_000L
        val out = FlightCore.finishPhase(state(r, data), data, endedAt)
        assertEquals(endedAt, out.record!!.time)
    }

    @Test
    fun `varis damgasi da fazin bittigi ani tasir`() {
        val r = route()
        val data = Persisted(routeKey = r.key, flight = ActiveFlight(r.key, r.km - r.kmPerLeg))
        val endedAt = now - 3 * 60 * 60_000L
        val out = FlightCore.finishPhase(state(r, data), data, endedAt)
        assertTrue(out.arrived)
        assertEquals(endedAt, out.data.log[0].time)
    }

    @Test
    fun `varista ucus inmis olarak kalir ve gunluge tamamlanmis satir duser`() {
        val r = route()
        val data = Persisted(routeKey = r.key, flight = ActiveFlight(r.key, r.km - r.kmPerLeg, start = now - 1000L))
        val out = FlightCore.finishPhase(state(r, data), data, now)

        assertTrue(out.data.flight!!.landed)
        assertEquals(now, out.data.flight!!.landedAt)
        assertTrue(out.data.log[0].done)
        assertEquals(now - 1000L, out.data.log[0].start)
        assertEquals(r.km, out.data.log[0].flown, 1e-9)
    }

    @Test
    fun `ucus yokken biten oturum bu rotada ucus baslatir`() {
        val r = route()
        val data = Persisted(routeKey = r.key)
        val out = FlightCore.finishPhase(state(r, data), data, now)
        assertEquals(r.key, out.data.flight!!.routeKey)
        assertEquals(r.kmPerLeg, out.data.flight!!.flown, 1e-9)
    }

    @Test
    fun `rota tamamlanana kadar gunluge damga dusmez`() {
        val r = route()
        var data = Persisted(routeKey = r.key)
        repeat(r.legs - 1) {
            val out = FlightCore.finishPhase(state(r, data), data, now)
            assertTrue(out.data.log.isEmpty())
            data = out.data
        }
        val last = FlightCore.finishPhase(state(r, data), data, now)
        assertEquals(1, last.data.log.size)
        assertEquals(r.km.toInt(), last.data.log[0].km)
    }
}
