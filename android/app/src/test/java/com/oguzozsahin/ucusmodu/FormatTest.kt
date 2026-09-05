package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.formatClock
import com.oguzozsahin.ucusmodu.data.upperTr
import com.oguzozsahin.ucusmodu.data.formatHours
import com.oguzozsahin.ucusmodu.data.formatNumber
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun `saat bicimi web surumuyle ayni`() {
        assertEquals("25:00", formatClock(1_500_000))
        assertEquals("01:01", formatClock(61_000))
        assertEquals("00:00", formatClock(0))
        assertEquals("00:00", formatClock(-5_000))
        // kalan sure yukari yuvarlanir: 999 ms hala "00:01"
        assertEquals("00:01", formatClock(999))
    }

    @Test
    fun `sayilar Turkce binlik ayraciyla yazilir`() {
        assertEquals("12.346", formatNumber(12345.6))
        assertEquals("0", formatNumber(0.0))
        assertEquals("8.026", formatNumber(8026.398462))
    }

    @Test
    fun `toplam odak saati virgulle yazilir`() {
        assertEquals("1,3 sa", formatHours(4_500_000))
        assertEquals("0,0 sa", formatHours(0))
    }

    @Test
    fun `buyuk harf Turkce kurallarina uyar`() {
        // Kotlin'in uppercase'i yerelden bagimsiz: i -> I yapar, İ degil.
        // Ekranda "BILET KES", "HER ŞEYI SIL" yaziyordu.
        assertEquals("BİLET KES", "Bilet kes".upperTr())
        assertEquals("HER ŞEYİ SİL", "Her şeyi sil".upperTr())
        assertEquals("KABİN SESİ", "Kabin sesi".upperTr())
        assertEquals("KAT EDİLEN YOL", "Kat edilen yol".upperTr())
        assertEquals("İNİŞ YAPILDI", "İniş yapıldı".upperTr())

        // Noktasiz i buyuk harfte noktasiz kalir
        assertEquals("SIFIRLA", "Sıfırla".upperTr())
        assertEquals("VARIŞA", "Varışa".upperTr())
    }
}
