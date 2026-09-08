package com.oguzozsahin.ucusmodu

import com.oguzozsahin.ucusmodu.ui.window.CAP_ARC_STEP_RAD
import com.oguzozsahin.ucusmodu.ui.window.CAP_MERGE_RAD
import com.oguzozsahin.ucusmodu.ui.window.clampToHorizon
import com.oguzozsahin.ucusmodu.ui.window.clipToHalfSpace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pencere goruntusunde kara parcalarinin ufka oturtulmasi ve kirpilmasi.
 *
 * Buradaki asil sinav "Ege sinavi": kirpmayla yapilan ilk cozumde, agzi ufka
 * acilan bir korfezin iki ucunu birlestiren kiris korfezi bastan basa kara
 * yapiyordu. Bastirma bunu, halkayi kapali ve ayni yonde donen tek bir egri
 * olarak biraktigi icin kokten cozuyor — o ozellik burada olculuyor.
 */
class SkyRingsTest {

    /** Kameranin yukari yonu; ufuk bu eksene dik bir cember. */
    private val ux = 0.0
    private val uy = 0.0
    private val uz = 1.0

    /** Ufkun kosinusu: bu yuksekligin altindaki her sey ufkun otesinde. */
    private val cap = 0.9

    private fun buffer(points: Int) = DoubleArray(points * 3 + 16384)

    /** Yukselik [height], ufuk duzleminde [angle] acisinda duran birim nokta. */
    private fun point(height: Double, angle: Double): Triple<Float, Float, Float> {
        val ring = sqrt((1.0 - height * height).coerceAtLeast(0.0))
        return Triple(
            (ring * cos(angle)).toFloat(),
            (ring * sin(angle)).toFloat(),
            height.toFloat(),
        )
    }

    private fun ring(vararg points: Triple<Float, Float, Float>): FloatArray {
        val out = FloatArray(points.size * 3)
        points.forEachIndexed { i, (x, y, z) ->
            out[i * 3] = x
            out[i * 3 + 1] = y
            out[i * 3 + 2] = z
        }
        return out
    }

    private fun heightOf(out: DoubleArray, i: Int) =
        out[i * 3] * ux + out[i * 3 + 1] * uy + out[i * 3 + 2] * uz

    private fun lengthOf(out: DoubleArray, i: Int) =
        sqrt(out[i * 3] * out[i * 3] + out[i * 3 + 1] * out[i * 3 + 1] + out[i * 3 + 2] * out[i * 3 + 2])

    /** Ufka bastirilmis iki nokta arasindaki aci. */
    private fun angleBetween(out: DoubleArray, i: Int, j: Int): Double {
        fun horizontal(k: Int): DoubleArray {
            val h = heightOf(out, k)
            val x = out[k * 3] - h * ux
            val y = out[k * 3 + 1] - h * uy
            val z = out[k * 3 + 2] - h * uz
            val len = sqrt(x * x + y * y + z * z)
            return doubleArrayOf(x / len, y / len, z / len)
        }
        val a = horizontal(i)
        val b = horizontal(j)
        return acos((a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).coerceIn(-1.0, 1.0))
    }

    // ---------------- ufka bastirma ----------------

    @Test
    fun `tumuyle ufkun berisindeki halka oldugu gibi gecer`() {
        val src = ring(
            point(0.97, 0.0),
            point(0.96, 0.4),
            point(0.98, 0.8),
        )
        val out = buffer(3)
        val count = clampToHorizon(src, 3, out, ux, uy, uz, cap)

        assertEquals(3, count)
        for (i in 0 until 3) {
            assertEquals(src[i * 3].toDouble(), out[i * 3], 1e-9)
            assertEquals(src[i * 3 + 2].toDouble(), out[i * 3 + 2], 1e-9)
        }
    }

    @Test
    fun `ufkun otesindeki noktalar cembere tasinir, atilmaz`() {
        val src = ring(
            point(0.2, 0.0),
            point(0.1, 1.0),
            point(0.3, 2.0),
        )
        val out = buffer(3)
        val count = clampToHorizon(src, 3, out, ux, uy, uz, cap)

        assertTrue("hicbir nokta atilmamali", count >= 3)
        for (i in 0 until count) {
            assertEquals("cember uzerinde", cap, heightOf(out, i), 1e-9)
            assertEquals("birim kure uzerinde", 1.0, lengthOf(out, i), 1e-9)
        }
    }

    @Test
    fun `bastirilan noktalarin yonu korunur`() {
        // Nokta ufkun otesinde de olsa cembere kendi yonunde tasinmali.
        val angles = listOf(0.0, 1.3, 2.7, -2.0)
        val src = ring(*angles.map { point(0.1, it) }.toTypedArray())
        val out = buffer(angles.size)
        val count = clampToHorizon(src, angles.size, out, ux, uy, uz, cap)

        val ringRadius = sqrt(1.0 - cap * cap)
        for (angle in angles) {
            val expectedX = ringRadius * cos(angle)
            val expectedY = ringRadius * sin(angle)
            val found = (0 until count).any { i ->
                kotlin.math.abs(out[i * 3] - expectedX) < 1e-6 &&
                    kotlin.math.abs(out[i * 3 + 1] - expectedY) < 1e-6
            }
            assertTrue("$angle yonu cemberde bulunamadi", found)
        }
    }

    /**
     * Ege sinavi: agzi ufka acilan bir korfezin iki yakasi cemberde birbirine
     * kirisle degil, yay boyunca baglanmali. Kirisle baglansaydi arada kalan
     * su kara sayilirdi.
     */
    @Test
    fun `ufka acilan korfezin iki yakasi yay boyunca baglanir`() {
        // Kara: ufkun berisinde iki burun, aralarinda ufkun cok otesine
        // uzanan genis bir agiz.
        val src = ring(
            point(0.97, 0.10),
            point(0.05, 0.30),
            point(0.02, 3.00),
            point(0.05, 5.90),
            point(0.97, 6.10),
        )
        val out = buffer(5)
        val count = clampToHorizon(src, 5, out, ux, uy, uz, cap)
        assertTrue(count > 5)

        // Cembere dusen ardisik noktalar arasinda buyuk bir sicrama kalmamali:
        // her adim en fazla yay adimi kadar. Kirisle baglansaydi bu adim
        // radyanlar mertebesinde olurdu.
        var worst = 0.0
        for (i in 0 until count - 1) {
            val onRingA = kotlin.math.abs(heightOf(out, i) - cap) < 1e-9
            val onRingB = kotlin.math.abs(heightOf(out, i + 1) - cap) < 1e-9
            if (!onRingA || !onRingB) continue
            worst = maxOf(worst, angleBetween(out, i, i + 1))
        }
        assertTrue("cemberde $worst radyanlik kiris kaldi", worst <= CAP_ARC_STEP_RAD + 1e-9)
    }

    @Test
    fun `arkada kalan uzun kiyi seyreltilir`() {
        // Ufkun cok otesinde, cemberin ayni yerine dusen binlerce nokta.
        val n = 4000
        val src = FloatArray(n * 3)
        for (i in 0 until n) {
            val (x, y, z) = point(0.05, i * 1e-5)
            src[i * 3] = x
            src[i * 3 + 1] = y
            src[i * 3 + 2] = z
        }
        val out = buffer(n)
        val count = clampToHorizon(src, n, out, ux, uy, uz, cap)

        assertTrue("$n nokta birkac yuze inmeliydi, $count kaldi", count < 50)
        assertTrue(count >= 1)
    }

    @Test
    fun `cok yakin dusen noktalar birlestirilir`() {
        val src = ring(
            point(0.1, 0.0),
            point(0.1, CAP_MERGE_RAD / 4),
            point(0.1, CAP_MERGE_RAD / 2),
        )
        val out = buffer(3)
        assertEquals(1, clampToHorizon(src, 3, out, ux, uy, uz, cap))
    }

    @Test
    fun `tampon yetmezse yarim halka cizilmez`() {
        val n = 600
        val src = FloatArray(n * 3)
        for (i in 0 until n) {
            val (x, y, z) = point(0.1, i * 2.0 * Math.PI / n)
            src[i * 3] = x
            src[i * 3 + 1] = y
            src[i * 3 + 2] = z
        }
        // Kasitli olarak kucuk tampon.
        val out = DoubleArray(30)
        assertEquals(-1, clampToHorizon(src, n, out, ux, uy, uz, cap))
    }

    // ---------------- yari uzaya kirpma ----------------

    private fun square(z: Double) = doubleArrayOf(
        -1.0, -1.0, z,
        1.0, -1.0, z,
        1.0, 1.0, z,
        -1.0, 1.0, z,
    )

    @Test
    fun `tumuyle icerideki halka degismeden gecer`() {
        val src = square(0.0)
        val out = DoubleArray(64 * 3)
        val count = clipToHalfSpace(src, 4, out) { x, _, _ -> x + 5.0 }
        assertEquals(4, count)
        for (i in 0 until 12) assertEquals(src[i], out[i], 0.0)
    }

    @Test
    fun `tumuyle disaridaki halka yok olur`() {
        val out = DoubleArray(64 * 3)
        assertEquals(0, clipToHalfSpace(square(0.0), 4, out) { x, _, _ -> x - 5.0 })
    }

    @Test
    fun `duzlemi kesen halka sinirda kesilir`() {
        // x >= 0 yari uzayi: kare tam ortadan kesilir.
        val out = DoubleArray(64 * 3)
        val count = clipToHalfSpace(square(0.0), 4, out) { x, _, _ -> x }

        assertEquals(4, count)
        var onBoundary = 0
        for (i in 0 until count) {
            assertTrue("x negatif kalmamali", out[i * 3] >= -1e-12)
            if (kotlin.math.abs(out[i * 3]) < 1e-12) onBoundary++
        }
        assertEquals("iki kose sinira dusmeli", 2, onBoundary)
    }

    @Test
    fun `kesim noktasi dogrusal enterpolasyonla bulunur`() {
        // x >= 0.5: kesim (-1,-1) ile (1,-1) arasinda x=0.5'te olmali.
        val out = DoubleArray(64 * 3)
        val count = clipToHalfSpace(square(0.0), 4, out) { x, _, _ -> x - 0.5 }
        val crossing = (0 until count).filter { kotlin.math.abs(out[it * 3] - 0.5) < 1e-12 }
        assertEquals(2, crossing.size)
        assertTrue(crossing.any { kotlin.math.abs(out[it * 3 + 1] + 1.0) < 1e-12 })
        assertTrue(crossing.any { kotlin.math.abs(out[it * 3 + 1] - 1.0) < 1e-12 })
    }

    @Test
    fun `kirpmada tampon yetmezse yarim halka cizilmez`() {
        val out = DoubleArray(6)
        assertEquals(-1, clipToHalfSpace(square(0.0), 4, out) { x, _, _ -> x })
    }
}
