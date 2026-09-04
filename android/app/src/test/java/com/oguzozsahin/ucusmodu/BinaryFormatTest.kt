package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.ShapeFormat
import com.oguzozsahin.ucusmodu.data.PlaceFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * tools/build_coastline.py ve tools/build_places.py ile uretilen ikili
 * bicimlerin okunmasi. Once elde kurulan ornekler, sonra varsa gercek varliklar.
 */
class BinaryFormatTest {

    private fun bytes(block: ByteArrayOutputStream.() -> Unit) =
        ByteArrayOutputStream().apply(block).toByteArray()

    private fun ByteArrayOutputStream.int(value: Int) =
        write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array())

    private fun ByteArrayOutputStream.short(value: Int) =
        write(ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN).putShort(value.toShort()).array())

    @Test
    fun `sekil bicimi cozulur`() {
        val data = bytes {
            write("UMC1".toByteArray())
            int(1)
            int(2)
            // (0, 0) ve (90 dogu, 0)
            short(0); short(0)
            short(16383); short(0)
        }
        val shapes = ShapeFormat.parse(data)
        assertEquals(1, shapes.shapes.size)
        assertEquals(2, shapes.pointCount)

        // ilk nokta (0,0) -> birim vektor (1, 0, 0)
        val xyz = shapes.shapes[0].xyz
        assertEquals(1f, xyz[0], 1e-4f)
        assertEquals(0f, xyz[1], 1e-4f)
        assertEquals(0f, xyz[2], 1e-4f)
        // ikinci nokta 90 derece dogu -> (0, 1, 0)
        assertEquals(0f, xyz[3], 1e-3f)
        assertEquals(1f, xyz[4], 1e-3f)
    }

    @Test
    fun `yer listesi kaydi tam okunur`() {
        val city = "Istanbul".toByteArray(Charsets.UTF_8)
        val airport = "İstanbul".toByteArray(Charsets.UTF_8)
        val data = bytes {
            write("UMP2".toByteArray())
            int(1)
            short(5233)   // 28,75 doğu
            short(15030)  // 41,28 kuzey
            write(0)
            write("IST".toByteArray())
            write("TR".toByteArray())
            write(city.size); write(city)
            write(airport.size); write(airport)
        }
        val places = PlaceFormat.parse(data)
        assertEquals(1, places.size)
        val place = places[0]
        assertEquals("IST", place.code)
        assertEquals("Istanbul", place.city)
        assertEquals("İstanbul", place.airportName)
        assertEquals("TR", place.country)
        assertEquals(0, place.rank)
        // int16 kuantalama ~0,003 derece hassasiyet verir
        assertEquals(41.28, place.lat, 0.005)
        assertEquals(28.75, place.lon, 0.005)
    }

    @Test
    fun `gercek varliklar beklenen sayida kayit icerir`() {
        val places = File("src/main/assets/places.bin")
        if (places.exists()) {
            val parsed = PlaceFormat.parse(places.readBytes())
            assertEquals(3941, parsed.size)
            assertEquals(parsed.size, parsed.map { it.code }.toSet().size)
            assertTrue(parsed.any { it.code == "IST" })
        }
        val land = File("src/main/assets/land_110m.bin")
        if (land.exists()) {
            val parsed = ShapeFormat.parse(land.readBytes())
            assertEquals(128, parsed.shapes.size)
            assertEquals(5143, parsed.pointCount)
        }
        val lakes = File("src/main/assets/lakes_110m.bin")
        if (lakes.exists()) {
            val parsed = ShapeFormat.parse(lakes.readBytes())
            assertEquals(24, parsed.shapes.size)
            assertEquals(465, parsed.pointCount)
        }
    }
}
