package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.Airports
import com.oguzozsahin.ucusmodu.data.KM_PER_MINUTE
import com.oguzozsahin.ucusmodu.data.KM_PER_POMODORO
import com.oguzozsahin.ucusmodu.data.Presets
import com.oguzozsahin.ucusmodu.data.Route
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTest {

    @Test
    fun `IST-JFK rotasi masaustu surumuyle ayni`() {
        val route = Route.of("IST-JFK")!!
        assertEquals(8026.398462, route.km, 1e-6)
        assertEquals(23, route.legs)
        assertEquals(349.0, route.kmPerLeg, 0.5)
        assertEquals("TK9964", route.flightNo)
        assertEquals("İstanbul", route.origin.city)
        assertEquals("New York", route.dest.city)
    }

    @Test
    fun `olcek gercek seyir hizindan gelir`() {
        // 850 km/sa ile 25 dakika
        assertEquals(354.1667, KM_PER_POMODORO, 1e-3)
        assertEquals(14.1667, KM_PER_MINUTE, 1e-3)
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
        // bir pomodorodan kisa rotalar da tek pomodoroluk sayilir
        assertEquals(1, Route.of("IST-ESB")!!.legs)
        assertEquals(1, Route.legsFor(10.0))
        assertEquals(1, Route.legsFor(0.0))
        assertEquals(2, Route.legsFor(2 * KM_PER_POMODORO))
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
    fun `hazir rotalarin pomodoro sayilari masaustuyle ayni`() {
        val expected = mapOf(
            "IST-ESB" to 1, "IST-LHR" to 7, "IST-JFK" to 23,
            "CDG-HND" to 27, "SCL-AKL" to 27, "IST-SYD" to 42,
        )
        for ((key, legs) in expected) {
            assertEquals(key, legs, Route.of(key)!!.legs)
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

    @Test
    fun `bacak sayisi odak suresinden bagimsiz, oturum mesafesi olceklenir`() {
        val route = Route.of("IST-JFK")!!
        assertEquals(route.kmPerLeg, route.kmForFocus(25), 1e-9)
        assertEquals(2 * route.kmPerLeg, route.kmForFocus(50), 1e-9)
        assertEquals(1.48 * route.kmPerLeg, route.kmForFocus(37), 1e-9)

        // Rotanin odak suresi gercek seyir suresine esit; fark yalniz yuvarlama
        val focusMinutes = route.legs * 25
        assertTrue(kotlin.math.abs(focusMinutes - route.km / KM_PER_MINUTE) <= 12.5)
    }

    @Test
    fun `eski kayittaki bacaklar rotanin kendi olcegiyle km'ye cevrilir`() {
        val route = Route.of("IST-JFK")!!
        // 23 bacak, 8026 km: bacak basi 349 km, nominal 354 degil
        assertEquals(3 * route.kmPerLeg, Route.kmForLegs("IST-JFK", 3), 1e-9)

        // Cevrilen deger geri bacaga okundugunda ayni sayiyi vermeli
        val flown = Route.kmForLegs("IST-JFK", 3)
        assertEquals(3, kotlin.math.floor(flown / route.kmPerLeg + 1e-9).toInt())

        // Tamamlanmis rota rotanin uzunlugunu asmaz
        assertEquals(route.km, Route.kmForLegs("IST-JFK", route.legs), 1e-9)
        assertEquals(route.km, Route.kmForLegs("IST-JFK", 99), 1e-9)

        assertEquals(0.0, Route.kmForLegs("IST-JFK", 0), 1e-9)
        // Cozulemeyen rota nominal olcege duser
        assertEquals(2 * KM_PER_POMODORO, Route.kmForLegs("XXX-YYY", 2), 1e-9)
    }
}
