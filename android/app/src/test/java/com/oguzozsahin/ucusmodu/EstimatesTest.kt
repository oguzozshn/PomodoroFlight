package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.EARTH_CIRCUMFERENCE_KM
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.currentLapFraction
import com.oguzozsahin.ucusmodu.data.focusMinutesFor
import com.oguzozsahin.ucusmodu.data.formatDuration
import com.oguzozsahin.ucusmodu.data.gateOf
import com.oguzozsahin.ucusmodu.data.realFlightMinutes
import com.oguzozsahin.ucusmodu.data.remainingScheduleMinutes
import com.oguzozsahin.ucusmodu.data.worldLaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimatesTest {

    @Test
    fun `gercek ucus suresi seyir hizindan cikar`() {
        // 850 km/sa seyir + 30 dk manevra
        assertEquals(90, realFlightMinutes(850.0))
        assertEquals(30, realFlightMinutes(0.0))
        // IST-JFK gercekte ~10 saat
        assertTrue(realFlightMinutes(8026.4) in 570..600)
    }

    @Test
    fun `takvim suresi molalari da sayar`() {
        // tek pomodoro: hic mola yok
        assertEquals(25, remainingScheduleMinutes(1, 25, 5, 0))
        // iki pomodoro: aralarinda bir kisa mola
        assertEquals(55, remainingScheduleMinutes(2, 25, 5, 0))
        // dorduncu pomodorodan sonra uzun mola gelir (15 dk)
        assertEquals(25 * 5 + 5 * 3 + 15, remainingScheduleMinutes(5, 25, 5, 0))
        // dongu ilerlemisse uzun mola daha erken gelir
        assertEquals(25 * 2 + 15, remainingScheduleMinutes(2, 25, 5, 3))
        assertEquals(0, remainingScheduleMinutes(0, 25, 5, 0))
    }

    @Test
    fun `odak suresi bacak basinadir`() {
        assertEquals(225, focusMinutesFor(9, 25))
        assertEquals(0, focusMinutesFor(0, 25))
    }

    @Test
    fun `sure bicimi`() {
        assertEquals("45 dk", formatDuration(45))
        assertEquals("2 sa", formatDuration(120))
        assertEquals("9 sa 57 dk", formatDuration(597))
        assertEquals("0 dk", formatDuration(0))
    }

    @Test
    fun `kapi numarasi rotaya sabittir`() {
        assertEquals(gateOf("IST-JFK"), gateOf("IST-JFK"))
        val gate = gateOf("IST-JFK")
        assertTrue(gate.first() in "ABCD")
        assertTrue(gate.drop(1).toInt() in 1..30)
    }

    @Test
    fun `dunya turu ilerlemesi`() {
        assertEquals(0.0, worldLaps(0.0), 1e-9)
        assertEquals(1.0, worldLaps(EARTH_CIRCUMFERENCE_KM), 1e-9)
        assertEquals(2.5, worldLaps(EARTH_CIRCUMFERENCE_KM * 2.5), 1e-9)

        assertEquals(0f, currentLapFraction(0.0), 1e-6f)
        assertEquals(0.5f, currentLapFraction(EARTH_CIRCUMFERENCE_KM * 1.5), 1e-6f)
        // tam tur tamamlaninca yeni tur bastan baslar
        assertEquals(0f, currentLapFraction(EARTH_CIRCUMFERENCE_KM * 3), 1e-6f)
    }

    @Test
    fun `uzun mola suresi degismedi`() {
        assertEquals(15, FlightUiState.LONG_BREAK_MIN)
    }
}
