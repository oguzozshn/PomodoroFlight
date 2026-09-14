package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.ActiveFlight
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.Phase
import com.oguzozsahin.ucusmodu.data.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateTest {

    private val route = Route.of("IST-JFK")!!
    private val legs = route.legs

    private fun state(
        done: Number = 0,
        remainingMs: Long = 25 * 60_000L,
        phase: Phase = Phase.FOCUS,
        running: Boolean = true,
        focusMin: Int = 25,
    ) = FlightUiState(
        route = route,
        phase = phase,
        running = running,
        remainingMs = remainingMs,
        // Ilerleme km olarak saklaniyor; testler bacak sayisiyla konusuyor.
        flight = ActiveFlight("IST-JFK", done.toDouble() * route.kmPerLeg),
        log = emptyList(),
        focusMin = focusMin,
        breakMin = 5,
        autoBreak = true,
        sound = true,
        passenger = "",
        cycle = 0,
        totalMs = 0,
        totalKm = 0.0,
    )

    @Test
    fun `ilerleme orani suren pomodoroyu de sayar`() {
        // 3 bacak tamamlanmis, dorduncu pomodoro henuz baslamamis
        assertEquals(3f / legs, state(done = 3).fraction, 1e-6f)
        // dorduncu pomodoronun yarisi
        assertEquals(3.5f / legs, state(done = 3, remainingMs = 12 * 60_000L + 30_000L).fraction, 1e-3f)
        // dorduncu pomodoro bitmek uzere
        assertEquals(4f / legs, state(done = 3, remainingMs = 0).fraction, 1e-6f)
    }

    @Test
    fun `molada oran ilerlemez`() {
        val onBreak = state(done = 3, remainingMs = 0, phase = Phase.BREAK)
        assertEquals(3f / legs, onBreak.fraction, 1e-6f)
    }

    @Test
    fun `varista oran birdir ve daha fazla ilerlemez`() {
        val arrived = state(done = legs)
        assertTrue(arrived.arrived)
        assertEquals(1f, arrived.fraction, 1e-6f)
        assertEquals(0, arrived.legsLeft)
        // fazladan ilerleme kaydi orani asirmaz
        assertEquals(1f, state(done = legs + 11).fraction, 1e-6f)
        assertEquals(legs, state(done = legs + 11).done)
    }

    @Test
    fun `irtifa kalkista tirmanir inise dogru alcalir`() {
        assertEquals(0, state(done = 0, remainingMs = 25 * 60_000L).altitudeM)
        // seyir yuksekligi
        assertEquals(11_300, state(done = legs / 2).altitudeM)
        // varista sifir
        assertEquals(0, state(done = legs).altitudeM)
        // kalkis ve inis arasinda hep pozitif
        for (done in 1 until legs) {
            assertTrue(state(done = done).altitudeM > 0)
        }
    }

    @Test
    fun `kalan bacak ve mesafe tutarli`() {
        val s = state(done = 3)
        assertEquals(legs - 3, s.legsLeft)
        assertFalse(s.arrived)
        assertEquals(s.route.km / legs, s.route.kmPerLeg, 1e-9)
        assertEquals(s.route.km * s.fraction, s.flownKm, 1e-3)
    }

    @Test
    fun `faz sureleri ayarlardan gelir`() {
        val s = state()
        assertEquals(25 * 60_000L, s.durationMsOf(Phase.FOCUS))
        assertEquals(5 * 60_000L, s.durationMsOf(Phase.BREAK))
        assertEquals(15 * 60_000L, s.durationMsOf(Phase.LONG))
    }

    @Test
    fun `odak suresi bacak sayisini degistirmez, suren oturumun payini degistirir`() {
        // 50 dakikalik oturumun yarisi bir tam bacak eder
        val half50 = state(done = 3, remainingMs = 25 * 60_000L, focusMin = 50)
        assertEquals(legs, half50.route.legs)
        assertEquals(3, half50.done)
        assertEquals(4f / legs, half50.fraction, 1e-3f)
    }

    @Test
    fun `yarim kalan bacagin kutusu kismen dolar`() {
        // 37 dakikalik oturum 1,48 bacak eder: bir bacak tam, ikincisi %48
        val s = state(done = 37.0 / 25.0, remainingMs = 0, phase = Phase.BREAK)
        assertEquals(1, s.done)
        assertEquals(0.48f, s.legFill, 1e-3f)
        // tam bacak sinirinda ve varista kutu bos
        assertEquals(0f, state(done = 2, remainingMs = 0, phase = Phase.BREAK).legFill, 1e-6f)
        assertEquals(0f, state(done = legs).legFill, 0f)
    }

    @Test
    fun `hic yol alinmamis rota baslanmamis sayilir`() {
        assertFalse(state(done = 0).started)
        assertTrue(state(done = 1).started)
    }
}
