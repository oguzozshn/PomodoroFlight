package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.Phase
import com.oguzozsahin.ucusmodu.data.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateTest {

    private fun state(
        done: Int = 0,
        remainingMs: Long = 25 * 60_000L,
        phase: Phase = Phase.FOCUS,
        running: Boolean = true,
    ) = FlightUiState(
        route = Route.of("IST-JFK")!!,
        phase = phase,
        running = running,
        remainingMs = remainingMs,
        progress = mapOf("IST-JFK" to done),
        log = emptyList(),
        focusMin = 25,
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
        // 3/9 tamamlanmis, dorduncu pomodoro henuz baslamamis
        assertEquals(3f / 9f, state(done = 3).fraction, 1e-6f)
        // dorduncu pomodoronun yarisi
        assertEquals(3.5f / 9f, state(done = 3, remainingMs = 12 * 60_000L + 30_000L).fraction, 1e-3f)
        // dorduncu pomodoro bitmek uzere
        assertEquals(4f / 9f, state(done = 3, remainingMs = 0).fraction, 1e-6f)
    }

    @Test
    fun `molada oran ilerlemez`() {
        val onBreak = state(done = 3, remainingMs = 0, phase = Phase.BREAK)
        assertEquals(3f / 9f, onBreak.fraction, 1e-6f)
    }

    @Test
    fun `varista oran birdir ve daha fazla ilerlemez`() {
        val arrived = state(done = 9)
        assertTrue(arrived.arrived)
        assertEquals(1f, arrived.fraction, 1e-6f)
        assertEquals(0, arrived.legsLeft)
        // fazladan ilerleme kaydi orani asirmaz
        assertEquals(1f, state(done = 20).fraction, 1e-6f)
        assertEquals(9, state(done = 20).done)
    }

    @Test
    fun `irtifa kalkista tirmanir inise dogru alcalir`() {
        assertEquals(0, state(done = 0, remainingMs = 25 * 60_000L).altitudeM)
        // seyir yuksekligi
        assertEquals(11_300, state(done = 4).altitudeM)
        // varista sifir
        assertEquals(0, state(done = 9).altitudeM)
        // kalkis ve inis arasinda hep pozitif
        for (done in 1..8) {
            assertTrue(state(done = done).altitudeM > 0)
        }
    }

    @Test
    fun `kalan bacak ve mesafe tutarli`() {
        val s = state(done = 3)
        assertEquals(6, s.legsLeft)
        assertFalse(s.arrived)
        assertEquals(s.route.km / 9, s.route.kmPerLeg, 1e-9)
        assertEquals(s.route.km * s.fraction, s.flownKm, 1e-3)
    }

    @Test
    fun `faz sureleri ayarlardan gelir`() {
        val s = state()
        assertEquals(25 * 60_000L, s.durationMsOf(Phase.FOCUS))
        assertEquals(5 * 60_000L, s.durationMsOf(Phase.BREAK))
        assertEquals(15 * 60_000L, s.durationMsOf(Phase.LONG))
    }
}
