package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.EARTH_CIRCUMFERENCE_KM
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.cruiseMinutes
import com.oguzozsahin.ucusmodu.data.currentLapFraction
import com.oguzozsahin.ucusmodu.data.focusMinutesFor
import com.oguzozsahin.ucusmodu.data.formatDuration
import com.oguzozsahin.ucusmodu.data.gateOf
import com.oguzozsahin.ucusmodu.data.remainingScheduleMinutes
import com.oguzozsahin.ucusmodu.data.worldLaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EstimatesTest {

    @Test
    fun `gercek seyir suresi seyir hizindan cikar`() {
        // 850 km/sa; kalkis, taksi ve inis payi yok: pomodoro olcegiyle ayni tanim
        assertEquals(60, cruiseMinutes(850.0))
        assertEquals(0, cruiseMinutes(0.0))
        assertEquals(567, cruiseMinutes(8026.4))
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
        // 50 dakikalik oturumda iki pomodoro tek oturumdur; araya mola girmez
        assertEquals(50, remainingScheduleMinutes(2, 50, 5, 0))
        // IST-JFK 23 pomodoro: 575 dk odak + 5 uzun (75) + 17 kisa (85) mola
        assertEquals(735, remainingScheduleMinutes(23, 25, 5, 0))
        assertEquals(810, remainingScheduleMinutes(23, 25, 5, 0, longBreakMin = 30))
    }

    @Test
    fun `odak suresi pomodoro basina yirmi bes dakika`() {
        assertEquals(225, focusMinutesFor(9))
        assertEquals(575, focusMinutesFor(23))
        assertEquals(0, focusMinutesFor(0))
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
