package com.oguzozsahin.ucusmodu.ui.window

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import com.oguzozsahin.ucusmodu.data.ShapeSet

/** Ufuk egrisinin kac parcaya bolunerek cizilecegi. */
private const val HORIZON_STEPS = 96

/** Ufuk, bakis yonunun bu kadar sagina ve soluna kadar orneklenir (derece). */
private const val HORIZON_SPREAD = 80.0

/** Kameranin hemen onundeki kirpma duzlemi (dunya yaricapi biriminde). */
private const val NEAR = 1e-7

/** Gorus piramidi kenarlardan biraz genis tutulur; kirpik cerceveye degmesin. */
private const val FRUSTUM_MARGIN = 1.06

/**
 * Ufuk cizgisinin ekran noktalari: `[x0, y0, x1, y1, ...]`.
 *
 * Nokta nokta hesaplandigi icin dunyanin egrilgi kendiliginden cikar; yuksek
 * irtifada ufkun hafif kavisi da, alcaktayken duzlesmesi de dogru olur.
 */
fun horizonPolyline(camera: SkyCamera): FloatArray {
    val angle = camera.horizonDip
    val out = FloatArray((HORIZON_STEPS + 1) * 2)
    var n = 0
    for (i in 0..HORIZON_STEPS) {
        val offset = Math.toRadians(-HORIZON_SPREAD + 2 * HORIZON_SPREAD * i / HORIZON_STEPS)
        val p = camera.surfaceAt(angle, camera.azimuthRad + offset)
        val vx = p[0] - camera.posX
        val vy = p[1] - camera.posY
        val vz = p[2] - camera.posZ
        val depth = camera.depth(vx, vy, vz)
        if (depth <= NEAR) continue
        out[n++] = camera.screenX(vx, vy, vz, depth)
        out[n++] = camera.screenY(vx, vy, vz, depth)
    }
    return out.copyOf(n)
}

/** Ufkun altinda kalan, yani yer yuzeyinin kapladigi alan. */
fun groundPath(camera: SkyCamera, horizon: FloatArray): Path {
    val path = Path()
    if (horizon.size < 4) return path
    val left = -camera.width
    val right = camera.width * 2f
    val bottom = camera.height * 2f
    path.moveTo(left, horizon[1])
    var i = 0
    while (i < horizon.size) {
        path.lineTo(horizon[i], horizon[i + 1])
        i += 2
    }
    path.lineTo(right, horizon[horizon.size - 1])
    path.lineTo(right, bottom)
    path.lineTo(left, bottom)
    path.close()
    return path
}

/**
 * Ufka kadar gorunen kara parcalari.
 *
 * Her halkanin ufuk otesinde kalan kismi once ufuk cizgisine bastirilir, sonra
 * halka gorus piramidinin bes duzlemine kirpilir; kalan koseler ekrana dusurulur.
 */
fun landPath(camera: SkyCamera, shapes: ShapeSet): Path {
    // Halkalar ic ice gecmez (goller ayri katmanda durur), bu yuzden NonZero:
    // ufuk sinirinda birlesen parcalar delik acmaz, kara kara kalir.
    val path = Path().apply { fillType = PathFillType.NonZero }
    val ux = camera.upX
    val uy = camera.upY
    val uz = camera.upZ
    val cosCap = camera.horizonCos.toFloat()

    // Gorus piramidinin dort yani. Duzlemlerin hepsi kameradan gectigi icin
    // kirpilan noktalar da ekran cercevesinin uzerine duser; boylece ufkun
    // arkasina dolanan yay milyonlarca piksellik koordinat uretmez.
    val tanH = camera.tanHalfHorizontal * FRUSTUM_MARGIN
    val tanV = camera.tanHalfVertical * FRUSTUM_MARGIN
    val insideFront = { x: Double, y: Double, z: Double ->
        camera.depth(x - camera.posX, y - camera.posY, z - camera.posZ) - NEAR
    }
    val insideRight = { x: Double, y: Double, z: Double ->
        val vx = x - camera.posX
        val vy = y - camera.posY
        val vz = z - camera.posZ
        tanH * camera.depth(vx, vy, vz) - camera.lateral(vx, vy, vz)
    }
    val insideLeft = { x: Double, y: Double, z: Double ->
        val vx = x - camera.posX
        val vy = y - camera.posY
        val vz = z - camera.posZ
        tanH * camera.depth(vx, vy, vz) + camera.lateral(vx, vy, vz)
    }
    val insideTop = { x: Double, y: Double, z: Double ->
        val vx = x - camera.posX
        val vy = y - camera.posY
        val vz = z - camera.posZ
        tanV * camera.depth(vx, vy, vz) - camera.vertical(vx, vy, vz)
    }
    val insideBottom = { x: Double, y: Double, z: Double ->
        val vx = x - camera.posX
        val vy = y - camera.posY
        val vz = z - camera.posZ
        tanV * camera.depth(vx, vy, vz) + camera.vertical(vx, vy, vz)
    }

    var buffer = DoubleArray(0)
    var scratch = DoubleArray(0)

    for (shape in shapes.shapes) {
        if (!shape.withinCap(ux.toFloat(), uy.toFloat(), uz.toFloat(), cosCap)) continue
        val n = shape.pointCount
        if (n < 3) continue

        val need = n * 3 + 16384
        if (buffer.size < need) {
            buffer = DoubleArray(need)
            scratch = DoubleArray(need)
        }

        // Ufkun otesi atilmaz, ufka bastirilir; arkada kalan uzun kiyilar da
        // ayni anda seyreltilir, yoksa bir kitanin on bin noktasi bosuna gezilir.
        var count = clampToHorizon(shape.xyz, n, buffer, ux, uy, uz, camera.horizonCos)
        if (count < 3) continue

        count = clipToHalfSpace(buffer, count, scratch, insideFront)
        if (count < 3) continue
        System.arraycopy(scratch, 0, buffer, 0, count * 3)
        count = clipToHalfSpace(buffer, count, scratch, insideRight)
        if (count < 3) continue
        System.arraycopy(scratch, 0, buffer, 0, count * 3)
        count = clipToHalfSpace(buffer, count, scratch, insideLeft)
        if (count < 3) continue
        System.arraycopy(scratch, 0, buffer, 0, count * 3)
        count = clipToHalfSpace(buffer, count, scratch, insideTop)
        if (count < 3) continue
        System.arraycopy(scratch, 0, buffer, 0, count * 3)
        count = clipToHalfSpace(buffer, count, scratch, insideBottom)
        if (count < 3) continue

        var started = false
        for (i in 0 until count) {
            val x = scratch[i * 3]
            val y = scratch[i * 3 + 1]
            val z = scratch[i * 3 + 2]
            val vx = x - camera.posX
            val vy = y - camera.posY
            val vz = z - camera.posZ
            val depth = camera.depth(vx, vy, vz)
            if (depth <= NEAR) continue
            val sx = camera.screenX(vx, vy, vz, depth)
            val sy = camera.screenY(vx, vy, vz, depth)
            if (!started) {
                path.moveTo(sx, sy)
                started = true
            } else {
                path.lineTo(sx, sy)
            }
        }
        if (started) path.close()
    }
    return path
}
