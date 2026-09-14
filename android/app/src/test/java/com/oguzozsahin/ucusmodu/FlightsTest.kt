package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.ActiveFlight
import com.oguzozsahin.ucusmodu.data.Flights
import com.oguzozsahin.ucusmodu.data.LogEntry
import com.oguzozsahin.ucusmodu.data.Persisted
import com.oguzozsahin.ucusmodu.data.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

/** Tek etkin ucus: baslatma, bitirme, gunluk satiri, kure izleri ve eski kaydin donusumu. */
class FlightsTest {

    private val now = 1_757_030_400_000L
    private val jfk = Route.of("IST-JFK")!!
    private val lhr = Route.of("IST-LHR")!!

    @Test
    fun `bilet koparilinca ucus sifirdan baslar`() {
        val data = Flights.start(Persisted(flight = ActiveFlight(jfk.key, 5000.0)), jfk.key, now)
        assertEquals(ActiveFlight(jfk.key, flown = 0.0, start = now), data.flight)
    }

    @Test
    fun `sifir km'lik yarim bitis satir yazmaz`() {
        val data = Flights.end(Flights.start(Persisted(), jfk.key, now), jfk, done = false, now = now + 1)
        assertNull(data.flight)
        assertTrue(data.log.isEmpty())
    }

    @Test
    fun `yarida bitirilen ucus gunluge yarim duser`() {
        val started = Flights.start(Persisted(), jfk.key, now)
        val flying = started.copy(flight = started.flight!!.copy(flown = 2443.8))
        val data = Flights.end(flying, jfk, done = false, now = now + 5)

        assertNull(data.flight)
        val row = data.log.single()
        assertFalse(row.done)
        assertEquals(2443.8, row.flown, 1e-9)
        assertEquals(8026, row.km)
        assertEquals(now, row.start)
        assertEquals(now + 5, row.time)
    }

    @Test
    fun `varista satir yazilir, ucus inmis kalir ve ikinci kez yazilmaz`() {
        val data = Flights.end(Persisted(flight = ActiveFlight(jfk.key, jfk.km, now)), jfk, done = true, now = now + 9)
        assertTrue(data.log.single().done)
        assertTrue(data.flight!!.landed)
        assertEquals(now + 9, data.flight!!.landedAt)
        assertSame(data, Flights.end(data, jfk, done = false, now = now + 10))
    }

    @Test
    fun `baska rotanin ucusu kapatilmaz`() {
        val data = Persisted(flight = ActiveFlight(lhr.key, 5.0))
        assertSame(data, Flights.end(data, jfk, done = false, now = now))
    }

    @Test
    fun `eski satir tamamlanmis sayilir`() {
        val old = LogEntry("IST-ESB", 380, now)
        assertTrue(old.done)
        assertEquals(380.0, old.flown, 0.0)
        assertEquals(now, old.start)
    }

    @Test
    fun `sayimlar ve satir ozeti`() {
        val log = listOf(
            LogEntry("IST-ESB", 380, now),
            LogEntry("IST-JFK", 8026, now, flown = 698.0, done = false),
        )
        assertEquals(1, Flights.completedCount(log))
        assertEquals(1, Flights.partialCount(log))
        assertEquals("✓ 1 pom · 380 km", Flights.describe(log[0]))
        assertEquals("%8 · 2 / 23 pom · 698 km", Flights.describe(log[1]))
        assertEquals("2 / 23 pomodoro (698 km)", Flights.summary(jfk, 698.0))
    }

    @Test
    fun `kurede yarim ucusun yalniz uculan kismi cizilir, ayni rotanin en uzunu`() {
        val log = listOf(
            LogEntry("IST-JFK", 8026, now, flown = 2006.5, done = false),
            LogEntry("IST-JFK", 8026, now, flown = 4013.0, done = false),
            LogEntry("IST-ESB", 380, now),
        )
        val trails = Flights.trails(log).toMap()
        assertEquals(0.5f, trails.getValue("IST-JFK"), 1e-3f)
        assertEquals(1f, trails.getValue("IST-ESB"), 0f)
    }

    @Test
    fun `donusum - acik rota etkin ucus, digerleri yarim satir, varisi yazilmis olan atlanir`() {
        val log = listOf(LogEntry("IST-ESB", 380, now - 86_400_000L))
        val progress = mapOf(
            "IST-JFK" to 2443.0,
            "IST-LHR" to 700.0,
            "IST-ESB" to 379.54,
            "CDG-HND" to 0.0,
        )
        val (flight, rows) = Flights.fromProgress(progress, "IST-JFK", log, now)

        assertEquals(ActiveFlight("IST-JFK", 2443.0), flight)
        assertEquals(2, rows.size)
        val lhrRow = rows.single { it.routeKey == "IST-LHR" }
        assertFalse(lhrRow.done)
        assertEquals(700.0, lhrRow.flown, 1e-9)
        assertEquals(lhr.km.roundToInt(), lhrRow.km)
        assertEquals(1, rows.count { it.routeKey == "IST-ESB" })
        assertTrue(rows.none { it.routeKey == "CDG-HND" })
    }

    @Test
    fun `donusum - acik rota bossa etkin ucus yok`() {
        val (flight, rows) = Flights.fromProgress(mapOf("IST-LHR" to 700.0), "IST-JFK", emptyList(), now)
        assertNull(flight)
        assertEquals(1, rows.size)
    }
}
