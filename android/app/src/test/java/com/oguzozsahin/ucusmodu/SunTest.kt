package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.Sun
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Gunes konumu: kurenin gunduz/gece sinirini bu belirliyor. */
class SunTest {

    private fun subsolar(iso: String) = Sun.subsolarPoint(Instant.parse(iso).toEpochMilli())

    @Test
    fun `gundonumlerinde gunes doncelerde olur`() {
        // 21 Haziran: Yengec Doncesi
        assertEquals(23.44, subsolar("2026-06-21T12:00:00Z").lat, 0.1)
        // 21 Aralik: Oglak Doncesi
        assertEquals(-23.44, subsolar("2026-12-21T12:00:00Z").lat, 0.1)
    }

    @Test
    fun `ekinoksta gunes ekvatordadir`() {
        assertEquals(0.0, subsolar("2026-03-20T12:00:00Z").lat, 0.6)
        assertEquals(0.0, subsolar("2026-09-23T12:00:00Z").lat, 0.6)
    }

    @Test
    fun `ogle UTC'de gunes Greenwich uzerindedir`() {
        // zaman denklemi nedeniyle birkac derece sapabilir
        assertEquals(0.0, subsolar("2026-09-04T12:00:00Z").lon, 5.0)
    }

    @Test
    fun `alti saatte doksan derece bati kayar`() {
        val noon = subsolar("2026-09-04T12:00:00Z").lon
        val evening = subsolar("2026-09-04T18:00:00Z").lon
        var delta = evening - noon
        if (delta > 180) delta -= 360.0
        if (delta < -180) delta += 360.0
        assertEquals(-90.0, delta, 0.5)
    }

    @Test
    fun `boylam her zaman aralikta kalir`() {
        var t = Instant.parse("2026-01-01T00:00:00Z").toEpochMilli()
        repeat(400) {
            val point = Sun.subsolarPoint(t)
            assertTrue(point.lon in -180.0..180.0)
            assertTrue(point.lat in -23.5..23.5)
            t += 21_600_000L
        }
    }
}
