package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.Airport
import com.oguzozsahin.ucusmodu.data.PlaceRegistry
import com.oguzozsahin.ucusmodu.data.TurkishCities
import com.oguzozsahin.ucusmodu.data.normalizeForSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Arama, veri setindeki adlarin Turkce olmadigi durumlarda da bulmali:
 * OurAirports bazi havalimanlarini ilcesiyle yazar (ADB icin Gaziemir) ve
 * yabanci sehirleri Ingilizce yazar (Munich).
 */
class SearchTest {

    @Before
    fun setUp() {
        PlaceRegistry.install(
            listOf(
                Airport("IST", "Istanbul", 41.28, 28.75, "TR", 0, "İstanbul"),
                Airport("ADB", "Gaziemir", 38.29, 27.16, "TR", 0, "Adnan Menderes"),
                Airport("SAW", "Pendik, Istanbul", 40.90, 29.31, "TR", 0, "Istanbul Sabiha Gökçen"),
                Airport("MUC", "Munich", 48.35, 11.79, "DE", 0, "Munich"),
                Airport("LHR", "London", 51.47, -0.45, "GB", 0, "London Heathrow"),
                Airport("DIY", "Diyarbakır", 37.89, 40.20, "TR", 1, "Diyarbakır"),
                Airport("GRU", "São Paulo", -23.43, -46.47, "BR", 0, "Guarulhos"),
            ),
        )
    }

    private fun codes(query: String) = PlaceRegistry.search(query).map { it.code }

    @Test
    fun `Turkce yazim ve aksan farklari bulunur`() {
        assertEquals("istanbul", normalizeForSearch("İstanbul"))
        assertEquals("diyarbakir", normalizeForSearch("Diyarbakır"))
        assertEquals("sanliurfa", normalizeForSearch("Şanlıurfa"))
        assertEquals("sao paulo", normalizeForSearch("São Paulo"))
        assertTrue("DIY" in codes("diyarbakir"))
        assertTrue("DIY" in codes("Diyarbakır"))
        assertTrue("GRU" in codes("sao paulo"))
    }

    @Test
    fun `ilce adiyla yazilmis havalimanlari sehir adiyla bulunur`() {
        // veri setinde Gaziemir yaziyor, kullanici Izmir ariyor
        assertTrue("ADB" in codes("izmir"))
        assertTrue("SAW" in codes("istanbul"))
    }

    @Test
    fun `Turkce sehir adlari bulunur`() {
        assertTrue("MUC" in codes("munih"))
        assertTrue("MUC" in codes("Münih"))
        assertTrue("LHR" in codes("londra"))
    }

    @Test
    fun `IATA koduyla arama en uste cikar`() {
        assertEquals("IST", codes("IST").first())
        assertEquals("MUC", codes("muc").first())
    }

    @Test
    fun `gosterilen ad Turkce karsiliktir`() {
        assertEquals("İzmir", PlaceRegistry.find("ADB")?.city)
        assertEquals("Münih", PlaceRegistry.find("MUC")?.city)
        assertEquals("İstanbul", PlaceRegistry.find("IST")?.city)
    }

    @Test
    fun `bos arama sonuc dondurmez`() {
        assertTrue(PlaceRegistry.search("").isEmpty())
        assertTrue(PlaceRegistry.search("   ").isEmpty())
        assertTrue(PlaceRegistry.search("zzzzzz").isEmpty())
    }

    @Test
    fun `Turkce ad sozlugu tutarli`() {
        for ((code, name) in TurkishCities.byCode) {
            assertEquals("$code kodu üç harf değil", 3, code.length)
            assertTrue("$code için ad boş", name.isNotBlank())
        }
    }
}
