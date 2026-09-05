package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.haversine
import com.oguzozsahin.ucusmodu.data.latLonOf
import com.oguzozsahin.ucusmodu.data.slerp
import com.oguzozsahin.ucusmodu.data.vecOf
import org.junit.Assert.assertEquals
import org.junit.Test

/** Kure geometrisi: web surumuyle ayni sayilari uretmeye devam etmeli. */
class GeoTest {

    @Test
    fun `bilinen mesafeler dogru hesaplanir`() {
        // Istanbul - New York, web surumunun urettigi deger
        assertEquals(8026.398462, haversine(41.28, 28.75, 40.64, -73.78), 1e-6)
        // Istanbul - Ankara
        assertEquals(379.544126, haversine(41.28, 28.75, 40.13, 32.99), 1e-6)
        // ayni nokta
        assertEquals(0.0, haversine(41.28, 28.75, 41.28, 28.75), 1e-9)
    }

    @Test
    fun `ekvator uzerinde ceyrek tur dunya cevresinin dortte biridir`() {
        val quarter = haversine(0.0, 0.0, 0.0, 90.0)
        assertEquals(40_030.0 / 4, quarter, 1.0)
    }

    @Test
    fun `slerp uc noktalari korur`() {
        val a = vecOf(41.28, 28.75)
        val b = vecOf(40.64, -73.78)

        val start = latLonOf(slerp(a, b, 0.0))
        assertEquals(41.28, start.lat, 1e-9)
        assertEquals(28.75, start.lon, 1e-9)

        val end = latLonOf(slerp(a, b, 1.0))
        assertEquals(40.64, end.lat, 1e-9)
        assertEquals(-73.78, end.lon, 1e-9)
    }

    @Test
    fun `slerp orta noktasi iki uca esit uzakliktadir`() {
        val a = vecOf(41.28, 28.75)
        val b = vecOf(40.64, -73.78)
        val mid = latLonOf(slerp(a, b, 0.5))

        // web surumunun urettigi orta nokta
        assertEquals(54.214557, mid.lat, 1e-6)
        assertEquals(-22.861291, mid.lon, 1e-6)

        val toStart = haversine(mid.lat, mid.lon, 41.28, 28.75)
        val toEnd = haversine(mid.lat, mid.lon, 40.64, -73.78)
        assertEquals(toStart, toEnd, 1e-6)
    }
}
