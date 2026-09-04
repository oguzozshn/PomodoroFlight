package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.Airports
import com.oguzozsahin.ucusmodu.data.Presets
import com.oguzozsahin.ucusmodu.data.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {

    @Test
    fun `IST-JFK rotasi web surumuyle ayni`() {
        val route = Route.of("IST-JFK")!!
        assertEquals(8026.398462, route.km, 1e-6)
        assertEquals(9, route.legs)
        assertEquals("TK9964", route.flightNo)
        assertEquals("İstanbul", route.origin.city)
        assertEquals("New York", route.dest.city)
    }

    @Test
    fun `ucus numarasi rotaya sabittir`() {
        assertEquals(Route.of("IST-JFK")!!.flightNo, Route.of("IST-JFK")!!.flightNo)
        assertEquals("TK5553", Route.of("IST-ESB")!!.flightNo)
        assertTrue(Route.of("IST-JFK")!!.flightNo != Route.of("JFK-IST")!!.flightNo)
    }

    @Test
    fun `gecersiz rotalar kurulmaz`() {
        assertNull(Route.of("IST-IST"))
        assertNull(Route.of("XXX-JFK"))
        assertNull(Route.of("IST"))
        assertNull(Route.of(""))
    }

    @Test
    fun `bacak sayisi en az bir olur`() {
        // 850 km'den kisa rotalar da tek pomodoroluk sayilir
        assertEquals(1, Route.of("IST-ESB")!!.legs)
        assertEquals(1, Route.legsFor(10.0))
        assertEquals(1, Route.legsFor(0.0))
        assertEquals(2, Route.legsFor(1700.0))
    }

    @Test
    fun `hazir rotalarin hepsi kurulabilir`() {
        for (preset in Presets.list) {
            val route = Route.of(preset.key)
            assertNotNull("${preset.key} kurulamadı", route)
            assertTrue(route!!.km > 0)
            assertTrue(route.legs >= 1)
        }
    }

    @Test
    fun `havalimani tablosu tutarli`() {
        assertEquals(34, Airports.all.size)
        assertEquals(Airports.all.size, Airports.codesByCity.size)
        for ((code, airport) in Airports.all) {
            assertEquals(code, airport.code)
            assertTrue("$code enlemi geçersiz", airport.lat in -90.0..90.0)
            assertTrue("$code boylamı geçersiz", airport.lon in -180.0..180.0)
        }
    }
}
