package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.LatLon
import com.oguzozsahin.ucusmodu.ui.GlobeCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Ortografik kure kamerasi.
 *
 * En onemlisi sondaki sabitlemeli yakinlastirma: parmagin altindaki cografi
 * nokta olcek degisirken yerinde kalmali. Daha once bu yalnizca elle
 * denenmisti; burada her olcek ve her konum icin olculuyor.
 */
class GlobeCameraTest {

    private val cx = 300f
    private val cy = 400f
    private val radius = 220f

    private fun camera(lat: Double, lon: Double) = GlobeCamera(lat, lon, cx, cy, radius)

    // ---------------- izdusum ----------------

    @Test
    fun `kameranin baktigi nokta tam ortaya duser`() {
        val c = camera(41.0, 29.0)
        val p = c.at(LatLon(41.0, 29.0))
        assertEquals(cx, p.x, 1e-3f)
        assertEquals(cy, p.y, 1e-3f)
        assertTrue(p.visible)
    }

    @Test
    fun `kurenin arkasindaki nokta gorunmez`() {
        val c = camera(41.0, 29.0)
        assertFalse(c.at(LatLon(-41.0, 29.0 - 180.0)).visible)
    }

    @Test
    fun `ufuktaki nokta tam yaricap uzakliginda durur`() {
        // Merkezden 90 derece uzaktaki nokta ufkun uzerindedir.
        val c = camera(0.0, 0.0)
        val p = c.at(LatLon(0.0, 90.0))
        assertEquals(radius.toDouble(), hypot((p.x - cx).toDouble(), (p.y - cy).toDouble()), 1e-2)
    }

    @Test
    fun `kuzey ekranda yukari bakar`() {
        val c = camera(0.0, 0.0)
        val north = c.at(LatLon(30.0, 0.0))
        assertTrue("kuzey daha kucuk y", north.y < cy)
        assertEquals(cx, north.x, 1e-3f)
    }

    @Test
    fun `dogu ekranda saga bakar`() {
        val c = camera(0.0, 0.0)
        val east = c.at(LatLon(0.0, 30.0))
        assertTrue(east.x > cx)
        assertEquals(cy, east.y, 1e-3f)
    }

    @Test
    fun `taban birim ve dik`() {
        val c = camera(37.0, -122.0)
        val east = doubleArrayOf(c.eastX.toDouble(), c.eastY.toDouble(), c.eastZ.toDouble())
        val north = doubleArrayOf(c.northX.toDouble(), c.northY.toDouble(), c.northZ.toDouble())
        val up = doubleArrayOf(c.upX.toDouble(), c.upY.toDouble(), c.upZ.toDouble())
        for (v in listOf(east, north, up)) {
            assertEquals(1.0, sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]), 1e-6)
        }
        assertEquals(0.0, dot(east, north), 1e-6)
        assertEquals(0.0, dot(north, up), 1e-6)
        assertEquals(0.0, dot(up, east), 1e-6)
    }

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

    // ---------------- ters cevrim ----------------

    @Test
    fun `izdusum ve tersi ayni noktaya doner`() {
        val c = camera(41.0, 29.0)
        for (lat in -60..60 step 20) {
            for (lon in -60..60 step 20) {
                val point = LatLon(lat.toDouble(), 29.0 + lon)
                val p = c.at(point)
                if (!p.visible) continue
                val back = c.locate(p.x, p.y)
                assertNotNull("$point icin cozum yok", back)
                assertEquals(point.lat, back!!.lat, 1e-3)
                assertEquals(point.lon, normalize(back.lon), 1e-3)
            }
        }
    }

    @Test
    fun `kurenin disina dokunmak null verir`() {
        val c = camera(41.0, 29.0)
        assertNull(c.locate(cx + radius * 1.2f, cy))
        assertNull(c.locate(cx, cy - radius * 1.05f))
    }

    @Test
    fun `tam merkeze dokunmak kameranin kendi noktasini verir`() {
        val c = camera(-33.9, 151.2)
        val hit = c.locate(cx, cy)!!
        assertEquals(-33.9, hit.lat, 1e-6)
        assertEquals(151.2, hit.lon, 1e-6)
    }

    @Test
    fun `sifir yaricapli kamera cozum vermez`() {
        val c = GlobeCamera(0.0, 0.0, cx, cy, 0f)
        assertNull(c.locate(cx, cy))
        assertNull(c.solveCamera(LatLon(0.0, 0.0), cx, cy, 1f))
    }

    // ---------------- sabitlemeli yakinlastirma ----------------

    /**
     * Iddia buydu: parmagin altindaki nokta olcek degisirken kaymaz. Her
     * dokunus noktasi ve her olcek carpani icin kayma piksel cinsinden
     * olculuyor.
     */
    @Test
    fun `yakinlastirma parmagin altindaki noktayi yerinde tutar`() {
        val start = camera(41.0, 29.0)
        var worst = 0.0
        var checked = 0
        for (dx in -8..8) {
            for (dy in -8..8) {
                val x = cx + dx * radius / 10f
                val y = cy + dy * radius / 10f
                val anchor = start.locate(x, y) ?: continue
                for (factor in listOf(0.5f, 0.8f, 1.25f, 2f, 4f, 9f)) {
                    val solved = start.solveCamera(anchor, x, y, factor) ?: continue
                    val after = GlobeCamera(solved.lat, solved.lon, cx, cy, radius * factor)
                    val p = after.at(anchor)
                    assertTrue("sabitlenen nokta arkaya dusmemeli", p.visible)
                    val drift = hypot((p.x - x).toDouble(), (p.y - y).toDouble())
                    worst = maxOf(worst, drift)
                    checked++
                }
            }
        }
        assertTrue("hicbir ornek denenmedi", checked > 500)
        assertTrue("en buyuk kayma $worst piksel", worst < 0.01)
    }

    @Test
    fun `kutuptaki nokta sabitlenince kamera kutba bakar`() {
        val c = camera(0.0, 0.0)
        val pole = LatLon(90.0, 0.0)
        val p = c.at(pole)
        val solved = c.solveCamera(pole, p.x, p.y, 2f)
        assertNotNull(solved)
        val after = GlobeCamera(solved!!.lat, solved.lon, cx, cy, radius * 2f)
        val back = after.at(pole)
        assertEquals(p.x, back.x, 0.01f)
        assertEquals(p.y, back.y, 0.01f)
    }

    @Test
    fun `boylam sarmasi kaymaya yol acmaz`() {
        // Tarih cizgisinin iki yaninda: atan2 dalinin degismesi kaymamali.
        val c = camera(0.0, 179.0)
        for (lon in listOf(176.0, 179.5, -179.5, -177.0)) {
            val point = LatLon(5.0, lon)
            val p = c.at(point)
            assertTrue(p.visible)
            val solved = c.solveCamera(point, p.x, p.y, 3f)!!
            val after = GlobeCamera(solved.lat, solved.lon, cx, cy, radius * 3f)
            val back = after.at(point)
            assertEquals(p.x, back.x, 0.01f)
            assertEquals(p.y, back.y, 0.01f)
        }
    }

    @Test
    fun `ulasilamayan sabitleme null doner`() {
        // Kutup, ufka cok yakin bir piksele sabitlenemez: hicbir kamera
        // merkezi onu oraya getirmez.
        val c = camera(0.0, 0.0)
        val solved = c.solveCamera(LatLon(89.9, 0.0), cx + radius * 0.999f, cy, 1f)
        assertNull(solved)
    }

    @Test
    fun `uzaklasirken kure disina dusen nokta cozum vermez`() {
        val c = camera(0.0, 0.0)
        // Uzaklasma (radiusScale < 1) ile ayni pikselde kalmasi istenen nokta
        // kurenin gorunen dairesinin disinda kalabilir.
        assertNull(c.solveCamera(LatLon(0.0, 0.0), cx + radius * 0.9f, cy, 0.5f))
    }

    // ---------------- yardimci ----------------

    private fun normalize(lon: Double): Double {
        var v = lon
        while (v > 180.0) v -= 360.0
        while (v < -180.0) v += 360.0
        return v
    }

    @Test
    fun `gorunurluk sinirini ufuk belirler`() {
        val c = camera(0.0, 0.0)
        // 89.9 derece uzaklikta hala gorunur, 90.1'de degil.
        assertTrue(c.at(LatLon(0.0, 89.9)).visible)
        assertFalse(c.at(LatLon(0.0, 90.1)).visible)
    }

    @Test
    fun `olcek buyudukce ayni cografi acikligin ekran karsiligi buyur`() {
        val a = GlobeCamera(0.0, 0.0, cx, cy, radius)
        val b = GlobeCamera(0.0, 0.0, cx, cy, radius * 3f)
        val point = LatLon(0.0, 10.0)
        val da = abs(a.at(point).x - cx)
        val db = abs(b.at(point).x - cx)
        assertEquals(3.0, (db / da).toDouble(), 1e-4)
        // Ufuktaki bir noktanin ekran uzakligi tam olarak yaricap kadardir.
        assertEquals(
            (radius * 3f).toDouble(),
            abs(b.at(LatLon(0.0, 90.0)).x - cx).toDouble(),
            1e-2,
        )
    }
}
