package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.Airports
import com.oguzozsahin.ucusmodu.data.Presets
import com.oguzozsahin.ucusmodu.data.KM_PER_MINUTE
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

    @Test
    fun `bacak sayisi pomodoro uzunluguna gore olceklenir`() {
        val route = Route.of("IST-JFK")!!
        // Varsayilan 25 dakikada eski davranis birebir korunur
        assertEquals(9, Route.of("IST-JFK", 25)!!.legs)
        // Pomodoro uzadikca bacak azalir: 8026 km / (34 km-dakika x uzunluk)
        assertEquals(5, Route.of("IST-JFK", 50)!!.legs)   // 4.72 -> 5
        assertEquals(8, Route.of("IST-JFK", 30)!!.legs)   // 7.87 -> 8
        assertEquals(20, Route.of("IST-JFK", 12)!!.legs)  // 19.67 -> 20

        // Rotayi bitirmek icin gereken toplam odak suresi olcekten bagimsiz
        for (focus in listOf(15, 25, 30, 50)) {
            val legs = Route.of("IST-JFK", focus)!!.legs
            val minutes = legs * focus
            assertTrue(
                "focus=$focus dakika=$minutes",
                kotlin.math.abs(minutes - route.km / KM_PER_MINUTE) < focus,
            )
        }
    }

    @Test
    fun `kisa rota her uzunlukta en az bir bacak`() {
        assertEquals(1, Route.legsFor(10.0, 50))
        assertEquals(1, Route.legsFor(0.0, 90))
    }

    @Test
    fun `eski kayittaki bacaklar rotanin kendi olcegiyle km'ye cevrilir`() {
        val route = Route.of("IST-JFK")!!
        // 9 bacak, 8026 km: bacak basi 892 km, 850 degil
        assertEquals(3 * route.kmPerLeg, Route.kmForLegs("IST-JFK", 3), 1e-9)

        // Cevrilen deger geri bacaga okundugunda ayni sayiyi vermeli;
        // 850 ile cevirmek burada 3 yerine 2 gosteriyordu.
        val flown = Route.kmForLegs("IST-JFK", 3)
        assertEquals(3, kotlin.math.floor(flown / route.kmPerLeg + 1e-9).toInt())

        // Tamamlanmis rota rotanin uzunlugunu asmaz
        assertEquals(route.km, Route.kmForLegs("IST-JFK", 9), 1e-9)
        assertEquals(route.km, Route.kmForLegs("IST-JFK", 99), 1e-9)

        assertEquals(0.0, Route.kmForLegs("IST-JFK", 0), 1e-9)
        // Cozulemeyen rota varsayilan olcege duser
        assertEquals(2 * 850.0, Route.kmForLegs("XXX-YYY", 2), 1e-9)
    }
}
