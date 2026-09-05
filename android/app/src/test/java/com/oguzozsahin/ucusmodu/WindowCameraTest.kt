package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.data.vecOf
import com.oguzozsahin.ucusmodu.ui.window.EARTH_RADIUS_KM
import com.oguzozsahin.ucusmodu.ui.window.SkyCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Pencere kamerasinin geometrisi. Compose'a hic dokunmadigi icin duz JVM
 * testiyle dogrulanabiliyor; ufuk, odak ve izdusum kapali formullerle karsilastirilir.
 */
class WindowCameraTest {

    private val width = 1080f
    private val height = 2160f
    private val altitude = 11.3

    private fun camera(pitch: Double = 0.0) = SkyCamera(
        lat = 41.0,
        lon = 29.0,
        altitudeKm = altitude,
        azimuthDeg = 0.0,
        pitchDeg = pitch,
        fovDeg = 60.0,
        width = width,
        height = height,
    )

    @Test
    fun `yerel yukari yon kuredeki noktayla ayni`() {
        val camera = camera()
        val expected = vecOf(41.0, 29.0)
        assertEquals(expected.x, camera.upX, 1e-12)
        assertEquals(expected.y, camera.upY, 1e-12)
        assertEquals(expected.z, camera.upZ, 1e-12)
    }

    @Test
    fun `bakis dogrultusu ekranin ortasina duser`() {
        val camera = camera()
        val forward = camera.direction(0.0, camera.azimuthRad)
        val depth = camera.depth(forward[0], forward[1], forward[2])
        assertEquals(1.0, depth, 1e-9)
        assertEquals(width / 2, camera.screenX(forward[0], forward[1], forward[2], depth), 1e-3f)
        assertEquals(height / 2, camera.screenY(forward[0], forward[1], forward[2], depth), 1e-3f)
    }

    @Test
    fun `odak uzakligi yari gorus acisindan gelir`() {
        assertEquals((height / 2) / tan(Math.toRadians(30.0)).toFloat(), camera().focal, 1e-3f)
    }

    @Test
    fun `ekrandan geri okunan yukseklik acisi ayni cikar`() {
        val camera = camera()
        for (degrees in listOf(-25.0, -10.0, 0.0, 7.5, 22.0)) {
            val direction = camera.direction(Math.toRadians(degrees), camera.azimuthRad)
            val depth = camera.depth(direction[0], direction[1], direction[2])
            val y = camera.screenY(direction[0], direction[1], direction[2], depth)
            val back = Math.toDegrees(atan2((height / 2 - y).toDouble(), camera.focal.toDouble()))
            assertEquals(degrees, back, 1e-3)
        }
    }

    @Test
    fun `ufuk acisi ve uzakligi kapali formulle ortusuyor`() {
        val camera = camera()
        val expectedDip = acos(1.0 / (1.0 + altitude / EARTH_RADIUS_KM))
        assertEquals(expectedDip, camera.horizonDip, 1e-12)

        val geometric = sqrt(
            (EARTH_RADIUS_KM + altitude) * (EARTH_RADIUS_KM + altitude) -
                EARTH_RADIUS_KM * EARTH_RADIUS_KM,
        )
        // Yay uzunlugu ile gorme mesafesi 11 km'de 1 km'den az ayrilir.
        assertTrue(Math.abs(camera.horizonKm - geometric) < 1.0)
    }

    @Test
    fun `asagi bakinca ufuk yukari kayar`() {
        val level = camera()
        val down = camera(pitch = -13.0)
        val dip = level.horizonDip

        fun horizonY(camera: SkyCamera): Float {
            val point = camera.surfaceAt(camera.horizonDip, camera.azimuthRad)
            val vx = point[0] - camera.posX
            val vy = point[1] - camera.posY
            val vz = point[2] - camera.posZ
            return camera.screenY(vx, vy, vz, camera.depth(vx, vy, vz))
        }

        val expected = height / 2 - down.focal * tan(Math.toRadians(13.0) - dip).toFloat()
        assertEquals(expected, horizonY(down), 0.5f)
        assertTrue(horizonY(down) < horizonY(level))
    }

    @Test
    fun `izdusumun tersi ayni noktaya geri goturur`() {
        val camera = camera(pitch = -13.0)
        for (x in listOf(80f, 540f, 990f)) {
            for (y in listOf(1400f, 1800f, 2100f)) {
                val hit = camera.surfaceHit(x, y) ?: continue
                // Yuzeye dustuyse birim kure uzerinde olmali
                val length = sqrt(hit[0] * hit[0] + hit[1] * hit[1] + hit[2] * hit[2])
                assertEquals(1.0, length, 1e-9)

                val vx = hit[0] - camera.posX
                val vy = hit[1] - camera.posY
                val vz = hit[2] - camera.posZ
                val depth = camera.depth(vx, vy, vz)
                assertEquals(x, camera.screenX(vx, vy, vz, depth), 1e-2f)
                assertEquals(y, camera.screenY(vx, vy, vz, depth), 1e-2f)
            }
        }
    }

    @Test
    fun `ufkun otesine bakan isin yuzeye dusmez`() {
        val camera = camera(pitch = -13.0)
        // Cercevenin ustu gokyuzu: kesisim olmamali
        assertTrue(camera.surfaceHit(width / 2, 40f) == null)
        // Alt taraf yer: kesisim olmali
        assertTrue(camera.surfaceHit(width / 2, height - 40f) != null)
    }
}
