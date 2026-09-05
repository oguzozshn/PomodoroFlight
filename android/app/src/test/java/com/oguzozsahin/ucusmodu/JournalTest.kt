package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.CardTier
import com.oguzozsahin.ucusmodu.data.EARTH_CIRCUMFERENCE_KM
import com.oguzozsahin.ucusmodu.data.FocusRecord
import com.oguzozsahin.ucusmodu.data.JournalFormat
import com.oguzozsahin.ucusmodu.data.JournalStats
import com.oguzozsahin.ucusmodu.data.kmToNextTier
import com.oguzozsahin.ucusmodu.data.nextTierOf
import com.oguzozsahin.ucusmodu.data.tierOf
import com.oguzozsahin.ucusmodu.data.tierProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Gunluk kaydinin bicimi ve ozetleri. Tamami saf oldugu icin cihaz gerekmiyor;
 * zaman dilimi disaridan verildigi icin sonuclar makineden bagimsiz.
 */
class JournalTest {

    private val zone = ZoneId.of("Europe/Istanbul")

    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDateTime.of(2026, 9, day, hour, minute).atZone(zone).toInstant().toEpochMilli()

    private fun record(
        day: Int,
        hour: Int,
        route: String = "IST-JFK",
        skipped: Boolean = false,
    ) = FocusRecord(at(day, hour), route, 25, 850.0, skipped)

    // ---------------- bicim ----------------

    @Test
    fun `kayit yazilip ayni sekilde geri okunur`() {
        val original = FocusRecord(1_757_030_400_000L, "IST-JFK", 25, 850.4, false)
        val decoded = JournalFormat.decode(JournalFormat.encode(original))
        assertEquals(original.time, decoded!!.time)
        assertEquals(original.routeKey, decoded.routeKey)
        assertEquals(original.minutes, decoded.minutes)
        assertEquals(original.km, decoded.km, 0.05)
        assertEquals(original.skipped, decoded.skipped)
    }

    @Test
    fun `atlanan kayit isaretini korur`() {
        val original = FocusRecord(1_757_030_400_000L, "SAW-CDG", 50, 2245.0, true)
        assertTrue(JournalFormat.decode(JournalFormat.encode(original))!!.skipped)
    }

    @Test
    fun `bozuk satirlar elenir`() {
        // baslik, bos satir, yarim yazilmis satir, sayi olmayan alan
        assertNull(JournalFormat.decode(JournalFormat.HEADER))
        assertNull(JournalFormat.decode(""))
        assertNull(JournalFormat.decode("   "))
        assertNull(JournalFormat.decode("1757030400000\tIST-JFK\t25"))
        assertNull(JournalFormat.decode("abc\tIST-JFK\t25\t850.0\t0"))
        assertNull(JournalFormat.decode("1757030400000\t\t25\t850.0\t0"))
        assertNull(JournalFormat.decode("-5\tIST-JFK\t25\t850.0\t0"))
    }

    @Test
    fun `km ayraci makineden bagimsizdir`() {
        // Turkce yerelde ondalik virgul olur; dosya her zaman nokta kullanmali
        val line = JournalFormat.encode(FocusRecord(1L, "IST-JFK", 25, 850.5, false))
        assertTrue(line, line.contains("850.5"))
        assertTrue(line, !line.contains(","))
    }

    // ---------------- ozetler ----------------

    @Test
    fun `atlanan pomodoro toplamlara girmez`() {
        val records = listOf(
            record(day = 10, hour = 9),
            record(day = 10, hour = 10, skipped = true),
        )
        assertEquals(1, JournalStats.counted(records).size)
        assertEquals(25, JournalStats.focusMinutes(records))
        assertEquals(1, JournalStats.dailyCounts(records, 1, LocalDate.of(2026, 9, 10), zone)[0].count)
    }

    @Test
    fun `gunluk sayim dogru kutulara duser`() {
        val records = listOf(
            record(day = 8, hour = 9),
            record(day = 10, hour = 9),
            record(day = 10, hour = 14),
            record(day = 10, hour = 23),
        )
        val counts = JournalStats.dailyCounts(records, 3, LocalDate.of(2026, 9, 10), zone)
        assertEquals(3, counts.size)
        assertEquals(LocalDate.of(2026, 9, 8), counts[0].date)
        assertEquals(1, counts[0].count)
        assertEquals(0, counts[1].count)
        assertEquals(3, counts[2].count)
    }

    @Test
    fun `gun siniri yerel saate gore cizilir`() {
        // 30 Eylul 23-30 yerel saatte hala 30 Eylul; UTC'de 1 Ekim olurdu
        val late = FocusRecord(at(30, 23, 30), "IST-JFK", 25, 850.0, false)
        assertEquals(LocalDate.of(2026, 9, 30), JournalStats.dateOf(late.time, zone))
    }

    @Test
    fun `seri ust uste gunleri sayar`() {
        val records = listOf(record(8, 9), record(9, 9), record(10, 9))
        assertEquals(3, JournalStats.streak(records, LocalDate.of(2026, 9, 10), zone))
    }

    @Test
    fun `bugun bos olsa da dun calisildiysa seri bozulmaz`() {
        val records = listOf(record(8, 9), record(9, 9))
        assertEquals(2, JournalStats.streak(records, LocalDate.of(2026, 9, 10), zone))
    }

    @Test
    fun `iki gun ara verilince seri kopar`() {
        val records = listOf(record(5, 9), record(6, 9), record(10, 9))
        assertEquals(1, JournalStats.streak(records, LocalDate.of(2026, 9, 10), zone))
    }

    @Test
    fun `hic kayit yoksa seri sifir`() {
        assertEquals(0, JournalStats.streak(emptyList(), LocalDate.of(2026, 9, 10), zone))
        // yalnizca atlanmis kayit da seri saymaz
        val skipped = listOf(record(10, 9, skipped = true))
        assertEquals(0, JournalStats.streak(skipped, LocalDate.of(2026, 9, 10), zone))
    }

    @Test
    fun `saat histogrami yerel saati kullanir`() {
        val records = listOf(record(10, 9), record(10, 9), record(11, 22))
        val hours = JournalStats.hourHistogram(records, zone)
        assertEquals(24, hours.size)
        assertEquals(2, hours[9])
        assertEquals(1, hours[22])
        assertEquals(3, hours.sum())
    }

    @Test
    fun `en yogun aralik ardisik iki saatten secilir`() {
        val records = listOf(record(10, 10), record(10, 11), record(10, 11), record(11, 15))
        assertEquals(10, JournalStats.peakWindow(JournalStats.hourHistogram(records, zone)))
        assertNull(JournalStats.peakWindow(IntArray(24)))
    }

    @Test
    fun `en cok uculan hatlar sayiya gore siralanir`() {
        val records = listOf(
            record(10, 9, route = "IST-JFK"),
            record(10, 10, route = "IST-JFK"),
            record(10, 11, route = "SAW-CDG"),
        )
        val top = JournalStats.topRoutes(records, 5)
        assertEquals("IST-JFK" to 2, top[0])
        assertEquals("SAW-CDG" to 1, top[1])
    }

    @Test
    fun `en yogun gun ve aktif gun sayisi`() {
        val records = listOf(record(8, 9), record(10, 9), record(10, 10))
        assertEquals(2, JournalStats.activeDays(records, zone))
        val busiest = JournalStats.busiestDay(records, zone)!!
        assertEquals(LocalDate.of(2026, 9, 10), busiest.date)
        assertEquals(2, busiest.count)
        assertNull(JournalStats.busiestDay(emptyList(), zone))
    }

    @Test
    fun `ugranan yerler rotanin iki ucundan cikar`() {
        val records = listOf(record(10, 9, route = "IST-JFK"), record(10, 10, route = "IST-CDG"))
        assertEquals(setOf("IST", "JFK", "CDG"), JournalStats.airports(records))
    }

    // ---------------- kart seviyesi ----------------

    @Test
    fun `kart seviyesi esiklere gore belirlenir`() {
        assertEquals(CardTier.CLASSIC, tierOf(0.0))
        assertEquals(CardTier.CLASSIC, tierOf(EARTH_CIRCUMFERENCE_KM - 1))
        assertEquals(CardTier.SILVER, tierOf(EARTH_CIRCUMFERENCE_KM))
        assertEquals(CardTier.ELITE, tierOf(EARTH_CIRCUMFERENCE_KM * 5))
        assertEquals(CardTier.ELITE_PLUS, tierOf(EARTH_CIRCUMFERENCE_KM * 20))
    }

    @Test
    fun `bir sonraki seviyeye ilerleme ve kalan mesafe`() {
        assertEquals(0f, tierProgress(0.0), 1e-6f)
        assertEquals(0.5f, tierProgress(EARTH_CIRCUMFERENCE_KM / 2), 1e-6f)
        assertEquals(EARTH_CIRCUMFERENCE_KM / 2, kmToNextTier(EARTH_CIRCUMFERENCE_KM / 2), 1e-6)

        // en ust seviyede sonraki yok, ilerleme dolu, kalan sifir
        val top = EARTH_CIRCUMFERENCE_KM * 30
        assertNull(nextTierOf(top))
        assertEquals(1f, tierProgress(top), 1e-6f)
        assertEquals(0.0, kmToNextTier(top), 1e-9)
    }
}
