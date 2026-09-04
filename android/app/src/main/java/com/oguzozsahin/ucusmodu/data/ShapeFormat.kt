package com.oguzozsahin.ucusmodu.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Tek bir kara/kiyi parcasi (poligon halkasi ya da cizgi). Noktalar birim kure uzerinde (x, y, z) uclusu olarak
 * saklanir; boylece her karede trigonometri yapmadan, yalnizca ic carpimla izdusum
 * alinabilir.
 */
class WorldShape(
    val xyz: FloatArray,
    private val cx: Float,
    private val cy: Float,
    private val cz: Float,
    private val sinRadius: Float,
) {
    val pointCount: Int get() = xyz.size / 3

    /** Parca tamamen kurenin arkasindaysa hic cizilmesin diye kaba eleme. */
    fun isHidden(ux: Float, uy: Float, uz: Float): Boolean =
        cx * ux + cy * uy + cz * uz < -sinRadius
}

class ShapeSet(val shapes: List<WorldShape>) {
    val pointCount: Int get() = shapes.sumOf { it.pointCount }
}

/** tools/build_coastline.py ile uretilen ikili bicimi okur. */
object ShapeFormat {

    private const val MAGIC = "UMC1"
    private const val LON_SCALE = 180f / 32767f
    private const val LAT_SCALE = 90f / 32767f

    /** tools/build_coastline.py ile uretilen ikili bicimi okur. */
    fun parse(bytes: ByteArray): ShapeSet {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val magic = ByteArray(4).also { buffer.get(it) }.toString(Charsets.US_ASCII)
        require(magic == MAGIC) { "Beklenmeyen kiyi seridi bicimi: $magic" }

        val lineCount = buffer.int
        val shapes = ArrayList<WorldShape>(lineCount)
        repeat(lineCount) {
            val pointCount = buffer.int
            val xyz = FloatArray(pointCount * 3)
            var sx = 0.0
            var sy = 0.0
            var sz = 0.0
            for (i in 0 until pointCount) {
                val lon = buffer.short * LON_SCALE
                val lat = buffer.short * LAT_SCALE
                val p = Math.toRadians(lat.toDouble())
                val l = Math.toRadians(lon.toDouble())
                val x = cos(p) * cos(l)
                val y = cos(p) * sin(l)
                val z = sin(p)
                xyz[i * 3] = x.toFloat()
                xyz[i * 3 + 1] = y.toFloat()
                xyz[i * 3 + 2] = z.toFloat()
                sx += x
                sy += y
                sz += z
            }
            shapes += buildShape(xyz, sx, sy, sz)
        }
        return ShapeSet(shapes)
    }

    /** Parcanin merkezini ve kapsadigi acisal yaricapi hesaplar (eleme icin). */
    private fun buildShape(xyz: FloatArray, sx: Double, sy: Double, sz: Double): WorldShape {
        val length = sqrt(sx * sx + sy * sy + sz * sz)
        if (length < 1e-6) {
            // Kureyi bastan basa gecen parca: elenmesin.
            return WorldShape(xyz, 0f, 0f, 1f, 1f)
        }
        val cx = (sx / length).toFloat()
        val cy = (sy / length).toFloat()
        val cz = (sz / length).toFloat()

        var minDot = 1f
        var i = 0
        while (i < xyz.size) {
            val dot = xyz[i] * cx + xyz[i + 1] * cy + xyz[i + 2] * cz
            if (dot < minDot) minDot = dot
            i += 3
        }
        val cosR = minDot.coerceIn(-1f, 1f)
        val sinR = sqrt((1f - cosR * cosR).coerceAtLeast(0f))
        return WorldShape(xyz, cx, cy, cz, sinR)
    }
}
