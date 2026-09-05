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

/** Ufuk cemberinde iki nokta arasi bu aciyi asarsa araya yay noktalari konur. */
private const val CAP_ARC_STEP_RAD = 0.05

/** Ufka bastirilmis iki komsu nokta bundan yakinsa ikincisi atlanir. */
private const val CAP_MERGE_RAD = 0.008

/**
 * Halkayi ufuk cemberine bastirir ve ayni gecicte seyreltir.
 *
 * Ufkun otesindeki nokta atilmaz, bulundugu yonde cembere tasinir: halka kapali
 * ve ayni yonde donen tek bir egri olarak kalir, bu yuzden korfezler kara
 * sanilmaz. Kirpmayla yapilan cozumde halkanin iki ucunu birlestiren kiris
 * Ege'yi bastan basa kara yapiyordu.
 *
 * Arkada kalan kiyilarin binlerce noktasi cemberin ayni yerine dustugu icin
 * birbirine cok yakin dusenler atlanir; arada buyuk bir sicrama varsa da yay
 * uzerine ara nokta konur. Boylece bir kitanin on bin noktasi birkac yuze iner.
 */
private fun clampToHorizon(
    src: FloatArray,
    count: Int,
    out: DoubleArray,
    ux: Double,
    uy: Double,
    uz: Double,
    cap: Double,
): Int {
    val ring = kotlin.math.sqrt((1.0 - cap * cap).coerceAtLeast(0.0))
    val capacity = out.size / 3
    var written = 0

    // En son cembere konan noktanin yonu
    var hasPrevious = false
    var px = 0.0
    var py = 0.0
    var pz = 0.0

    for (i in 0 until count) {
        val x = src[i * 3].toDouble()
        val y = src[i * 3 + 1].toDouble()
        val z = src[i * 3 + 2].toDouble()
        val height = x * ux + y * uy + z * uz

        if (height >= cap) {
            if (written >= capacity) return -1
            out[written * 3] = x
            out[written * 3 + 1] = y
            out[written * 3 + 2] = z
            written++
            hasPrevious = false
            continue
        }

        // Noktanin ufuk duzlemindeki yonu; buyuklugu bir yapilip cembere tasinir.
        var wx = x - height * ux
        var wy = y - height * uy
        var wz = z - height * uz
        val length = kotlin.math.sqrt(wx * wx + wy * wy + wz * wz)
        if (length < 1e-12) continue
        wx /= length
        wy /= length
        wz /= length

        if (hasPrevious) {
            val dot = (px * wx + py * wy + pz * wz).coerceIn(-1.0, 1.0)
            val angle = kotlin.math.acos(dot)
            if (angle < CAP_MERGE_RAD) continue
            if (angle > CAP_ARC_STEP_RAD) {
                // Yayin ikinci ekseni: onceki noktaya dik, bu noktaya bakan yon
                var ax = wx - dot * px
                var ay = wy - dot * py
                var az = wz - dot * pz
                val alen = kotlin.math.sqrt(ax * ax + ay * ay + az * az)
                if (alen > 1e-12) {
                    ax /= alen
                    ay /= alen
                    az /= alen
                    val steps = (angle / CAP_ARC_STEP_RAD).toInt().coerceAtMost(CAP_ARC_LIMIT)
                    for (k in 1 until steps) {
                        val t = angle * k / steps
                        val c = kotlin.math.cos(t)
                        val sn = kotlin.math.sin(t)
                        if (written >= capacity) return -1
                        out[written * 3] = cap * ux + ring * (px * c + ax * sn)
                        out[written * 3 + 1] = cap * uy + ring * (py * c + ay * sn)
                        out[written * 3 + 2] = cap * uz + ring * (pz * c + az * sn)
                        written++
                    }
                }
            }
        }

        if (written >= capacity) return -1
        out[written * 3] = cap * ux + ring * wx
        out[written * 3 + 1] = cap * uy + ring * wy
        out[written * 3 + 2] = cap * uz + ring * wz
        written++
        hasPrevious = true
        px = wx
        py = wy
        pz = wz
    }
    return written
}

/** Tek bir yayin en fazla kac parcaya bolunecegi. */
private const val CAP_ARC_LIMIT = 160

/**
 * Sutherland-Hodgman: kapali bir halkayi tek bir yari uzaya kirpar.
 * [inside] pozitif donerse nokta icerdedir; sinir uzerinde dogrusal enterpolasyon
 * yapilir. Cikti tamponu yetmezse -1 doner: yarim kalmis bir halka cizilmez.
 */
private inline fun clipToHalfSpace(
    src: DoubleArray,
    count: Int,
    out: DoubleArray,
    inside: (Double, Double, Double) -> Double,
): Int {
    val capacity = out.size / 3
    var written = 0
    val last = count - 1
    var prevX = src[last * 3]
    var prevY = src[last * 3 + 1]
    var prevZ = src[last * 3 + 2]
    var prevValue = inside(prevX, prevY, prevZ)

    for (i in 0 until count) {
        val x = src[i * 3]
        val y = src[i * 3 + 1]
        val z = src[i * 3 + 2]
        val value = inside(x, y, z)

        if (value >= 0.0) {
            if (prevValue < 0.0) {
                if (written >= capacity) return -1
                val t = prevValue / (prevValue - value)
                out[written * 3] = prevX + (x - prevX) * t
                out[written * 3 + 1] = prevY + (y - prevY) * t
                out[written * 3 + 2] = prevZ + (z - prevZ) * t
                written++
            }
            if (written >= capacity) return -1
            out[written * 3] = x
            out[written * 3 + 1] = y
            out[written * 3 + 2] = z
            written++
        } else if (prevValue >= 0.0) {
            if (written >= capacity) return -1
            val t = prevValue / (prevValue - value)
            out[written * 3] = prevX + (x - prevX) * t
            out[written * 3 + 1] = prevY + (y - prevY) * t
            out[written * 3 + 2] = prevZ + (z - prevZ) * t
            written++
        }

        prevX = x
        prevY = y
        prevZ = z
        prevValue = value
    }
    return written
}
