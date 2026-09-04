package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.BOARDING_MINUTES
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.Phase
import com.oguzozsahin.ucusmodu.data.Route
import com.oguzozsahin.ucusmodu.data.barcodePattern
import com.oguzozsahin.ucusmodu.data.issueTicket
import com.oguzozsahin.ucusmodu.data.passengerOrDefault
import com.oguzozsahin.ucusmodu.data.seatOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TicketTest {

    private val noon = 1_772_712_000_000L // sabit bir an

    private fun state(done: Int = 0, passenger: String = "") = FlightUiState(
        route = Route.of("IST-JFK")!!,
        phase = Phase.FOCUS,
        running = false,
        remainingMs = 25 * 60_000L,
        progress = mapOf("IST-JFK" to done),
        log = emptyList(),
        focusMin = 25,
        breakMin = 5,
        autoBreak = true,
        sound = true,
        passenger = passenger,
        cycle = 0,
        totalMs = 0,
        totalKm = 0.0,
    )

    @Test
    fun `koltuk ayni gun ayni rotada degismez`() {
        assertEquals(seatOf("IST-JFK", noon), seatOf("IST-JFK", noon + 3_600_000L))
        assertNotEquals(seatOf("IST-JFK", noon), seatOf("IST-LHR", noon))
        // ertesi gun yeni koltuk
        assertNotEquals(seatOf("IST-JFK", noon), seatOf("IST-JFK", noon + 86_400_000L))
    }

    @Test
    fun `koltuk gecerli bir yer gosterir`() {
        for (key in listOf("IST-JFK", "IST-ESB", "SCL-AKL", "CDG-HND")) {
            val seat = seatOf(key, noon)
            val row = seat.dropLast(1).toInt()
            assertTrue("$seat sıra dışı", row in 1..38)
            assertTrue("$seat koltuk harfi geçersiz", seat.last() in "ABCDEF")
        }
    }

    @Test
    fun `bilet saatleri sirali`() {
        val ticket = issueTicket(state(), noon)
        assertEquals(noon, ticket.boardingAt)
        assertEquals(noon + BOARDING_MINUTES * 60_000L, ticket.departureAt)
        assertTrue(ticket.arrivalAt > ticket.departureAt)
        // 9 pomodoro + molalar, binis payi dahil
        val minutes = (ticket.arrivalAt - ticket.boardingAt) / 60_000L
        assertEquals((BOARDING_MINUTES + 285).toLong(), minutes)
    }

    @Test
    fun `bilet rotadan dogru bilgileri alir`() {
        val ticket = issueTicket(state(done = 3, passenger = "oğuz"), noon)
        assertEquals("TK9964", ticket.flightNo)
        assertEquals("OĞUZ", ticket.passenger)
        assertEquals(6, ticket.legs)
        assertEquals(150, ticket.focusMinutes)
        assertEquals(8026.398462, ticket.distanceKm, 1e-6)
    }

    @Test
    fun `yolcu adi bos birakilabilir`() {
        assertEquals("YOLCU", passengerOrDefault(""))
        assertEquals("YOLCU", passengerOrDefault("   "))
        assertEquals("İNCİ", passengerOrDefault("inci"))
    }

    @Test
    fun `barkod deseni tohuma sabit`() {
        val a = barcodePattern("TK9964")
        val b = barcodePattern("TK9964")
        assertTrue(a.contentEquals(b))
        assertTrue(!a.contentEquals(barcodePattern("TK1234")))
        assertEquals(48, a.size)
        assertTrue(a.all { it in 1..3 })
    }
}
