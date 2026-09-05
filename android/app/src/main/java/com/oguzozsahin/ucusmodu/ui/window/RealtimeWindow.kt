package com.oguzozsahin.ucusmodu.ui.window

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import com.oguzozsahin.ucusmodu.data.Airport
import com.oguzozsahin.ucusmodu.data.PlaceRegistry
import com.oguzozsahin.ucusmodu.data.Route
import com.oguzozsahin.ucusmodu.data.ShapeSet
import com.oguzozsahin.ucusmodu.data.Sun
import com.oguzozsahin.ucusmodu.data.Vec3
import com.oguzozsahin.ucusmodu.data.WorldAsset
import com.oguzozsahin.ucusmodu.data.WorldShapes
import com.oguzozsahin.ucusmodu.data.bearing
import com.oguzozsahin.ucusmodu.data.vecOf
import kotlinx.coroutines.delay
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Pencereden bakis yonu: burnun bu kadar solu (derece). */
private const val VIEW_OFFSET_DEG = -78.0

/** Kameranin asagi egimi. */
private const val VIEW_PITCH_DEG = -13.0

/** Dikey gorus acisi. */
private const val VIEW_FOV_DEG = 62.0

/** Seyir hizi (km/sn) — bulutlarin gecis hizini belirler. */
private const val GROUND_SPEED_KM_S = 900.0 / 3600.0

private const val CLOUD_ALT_KM = 2.6
private const val CLOUD_COUNT = 190
private const val CLOUD_SPAN_U = 420.0
private const val CLOUD_SPAN_V = 320.0

private const val STAR_COUNT = 260

/** Ucus hic baslamadiysa bile pencere havada gorunsun. */
private const val MIN_ALTITUDE_M = 1400

/**
 * Gercek zamanli pencere: ucagin rota uzerindeki o anki yerinden, gercek saate
 * gore aydinlanan bir gokyuzu, ufuk kavisi, altta gecen kara parcalari,
 * bulut tabakasi ve geceleyin sehir isiklari.
 */
@Composable
fun RealtimeWindow(
    route: Route,
    fraction: Float,
    altitudeM: Int,
    /**
     * Calkanti ve bulut kaymasi icin akan saniye. Deger degil, okuyucu geciyor:
     * boylece saniye yalnizca cizim asamasinda okunur ve her karede butun
     * pencere ekrani yeniden bestelenmez.
     */
    time: () -> Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var land by remember { mutableStateOf<ShapeSet?>(null) }
    LaunchedEffect(Unit) {
        land = WorldShapes.load(context, WorldAsset.LAND_COARSE)
        WorldShapes.load(context, WorldAsset.LAND_FINE)?.let { land = it }
    }

    val places = remember { PlaceRegistry.all() }

    // Gunes konumu dakikada bir tazelenir; gunun saati gokyuzunun rengini belirler.
    val sun by produceState(initialValue = Sun.subsolarPoint(System.currentTimeMillis())) {
        while (true) {
            value = Sun.subsolarPoint(System.currentTimeMillis())
            delay(30_000L)
        }
    }

    val clouds = remember { CloudField(CLOUD_COUNT) }
    val stars = remember { StarField(STAR_COUNT) }
    val landCache = remember { LandCache() }

    val here = route.pointAt(fraction.toDouble())
    val ahead = route.pointAt((fraction + 0.004f).coerceAtMost(1f).toDouble())
    val heading = if (fraction >= 0.999f) {
        val behind = route.pointAt(0.996)
        bearing(behind.lat, behind.lon, here.lat, here.lon)
    } else {
        bearing(here.lat, here.lon, ahead.lat, ahead.lon)
    }
    val altitudeKm = altitudeM.coerceAtLeast(MIN_ALTITUDE_M) / 1000.0

    Canvas(modifier) {
        val t = time()
        val camera = SkyCamera(
            lat = here.lat,
            lon = here.lon,
            altitudeKm = altitudeKm,
            azimuthDeg = heading + VIEW_OFFSET_DEG,
            pitchDeg = VIEW_PITCH_DEG,
            fovDeg = VIEW_FOV_DEG,
            width = size.width,
            height = size.height,
        )

        val sunDir = vecOf(sun.lat, sun.lon)
        val sunUp = sunDir.x * camera.upX + sunDir.y * camera.upY + sunDir.z * camera.upZ
        val sunElevation = Math.toDegrees(asin(sunUp.coerceIn(-1.0, 1.0)))
        val palette = paletteFor(sunElevation)

        // Hafif turbulans: dunya sallanir, kanat sabit kalir.
        val roll = 0.7f * sin(t * 0.31f) + 0.28f * sin(t * 0.83f + 1.1f)
        val bob = size.height * 0.004f * sin(t * 0.47f + 0.6f)

        withTransform({
            rotate(roll, Offset(size.width / 2f, size.height * 0.62f))
            translate(0f, bob)
        }) {
            drawWorld(camera, palette, land, places, clouds, stars, sunDir, sunElevation, t, landCache)
        }
    }
}

/** Sahnenin butun katmanlari: gokyuzu, yildizlar, yer, kara, is, bulut. */
private fun DrawScope.drawWorld(
    camera: SkyCamera,
    palette: SkyPalette,
    land: ShapeSet?,
    places: List<Airport>,
    clouds: CloudField,
    stars: StarField,
    sunDir: Vec3,
    sunElevation: Double,
    time: Float,
    landCache: LandCache,
) {
    val horizon = horizonPolyline(camera)
    val horizonY = if (horizon.size >= 2) horizon[horizon.size / 2 or 1] else size.height / 2f

    // 1) gokyuzu: basucundan ufka inen degisim
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.zenith, palette.horizon),
            startY = -size.height * 0.25f,
            endY = horizonY,
        ),
    )

    // 2) gunesin bulundugu yonde ufka vuran sicak isik
    val sunPoint = projectDirection(camera, sunDir)
    if (sunPoint != null && palette.glowStrength > 0.02f) {
        val glowY = sunPoint.y.coerceIn(horizonY - size.height, horizonY + size.height * 0.15f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    palette.glow.copy(alpha = 0.55f * palette.glowStrength),
                    palette.glow.copy(alpha = 0.16f * palette.glowStrength),
                    Color.Transparent,
                ),
                center = Offset(sunPoint.x, glowY),
                radius = size.height * 0.9f,
            ),
            radius = size.height * 0.9f,
            center = Offset(sunPoint.x, glowY),
        )
    }

    // 3) yildizlar
    if (palette.night > 0.04f) {
        stars.draw(this, camera, palette.night)
    }

    // 4) gunes kursu
    if (sunPoint != null && sunElevation > -1.0) {
        val r = size.height * 0.028f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, palette.glow.copy(alpha = 0.9f), Color.Transparent),
                center = Offset(sunPoint.x, sunPoint.y),
                radius = r * 2.6f,
            ),
            radius = r * 2.6f,
            center = Offset(sunPoint.x, sunPoint.y),
        )
    }

    // 5) yer yuzeyi
    val ground = groundPath(camera, horizon)
    drawPath(ground, palette.sea)

    // 6) kara parcalari ve sehir isiklari yalnizca yerin uzerine dusebilir
    clipPath(ground) {
        land?.let { shapes ->
            drawPath(landCache.pathFor(camera, shapes), palette.land)
        }
        if (palette.night > 0.25f) {
            drawCityLights(camera, places, palette.night)
        }
    }

    // 7) hava perspektifi: uzaklik arttikca yer gokyuzunun rengine dogru silinir.
    // Ufuktan cerceve dibine kadar uzanir; kisa bir bant birakmak yerin oraya
    // kadar duz bir levha gibi gorunmesine yol aciyordu.
    val hazeTop = horizonY - size.height * 0.015f
    val hazeBottom = size.height * 1.02f
    if (hazeBottom > hazeTop) {
        drawRect(
            brush = Brush.verticalGradient(
                0f to palette.horizon.copy(alpha = 0.97f),
                0.10f to palette.horizon.copy(alpha = 0.74f),
                0.28f to palette.horizon.copy(alpha = 0.42f),
                0.58f to palette.horizon.copy(alpha = 0.20f),
                1f to palette.horizon.copy(alpha = 0.08f),
                startY = hazeTop,
                endY = hazeBottom,
            ),
            topLeft = Offset(0f, hazeTop),
            size = Size(size.width, hazeBottom - hazeTop),
        )
    }

    // 8) bulut tabakasi
    clouds.draw(this, camera, palette, time)
}

/** Gece sehirler kucuk sicak lekeler halinde gorunur. */
private fun DrawScope.drawCityLights(camera: SkyCamera, places: List<Airport>, night: Float) {
    var drawn = 0
    for (place in places) {
        if (drawn > 220) break
        val v = vecOf(place.lat, place.lon)
        if (v.x * camera.upX + v.y * camera.upY + v.z * camera.upZ < camera.horizonCos) continue
        val vx = v.x - camera.posX
        val vy = v.y - camera.posY
        val vz = v.z - camera.posZ
        val depth = camera.depth(vx, vy, vz)
        if (depth <= 1e-6) continue
        val x = camera.screenX(vx, vy, vz, depth)
        val y = camera.screenY(vx, vy, vz, depth)
        if (x < -40f || x > size.width + 40f || y < 0f || y > size.height) continue
        drawn++

        val weight = (1.4f - place.rank * 0.28f).coerceIn(0.45f, 1.4f)
        val r = size.height * 0.0032f * weight
        drawCircle(
            color = Color(0xFFFFD9A0).copy(alpha = 0.16f * night * weight),
            radius = r * 6f,
            center = Offset(x, y),
        )
        drawCircle(
            color = Color(0xFFFFF0C8).copy(alpha = 0.85f * night),
            radius = r,
            center = Offset(x, y),
        )
    }
}

private class ScreenXY(val x: Float, val y: Float)

/** Sonsuzdaki bir yonun (gunes, yildiz) ekrandaki yeri. */
private fun projectDirection(camera: SkyCamera, dir: Vec3): ScreenXY? {
    val depth = camera.depth(dir.x, dir.y, dir.z)
    if (depth <= 1e-6) return null
    return ScreenXY(
        camera.screenX(dir.x, dir.y, dir.z, depth),
        camera.screenY(dir.x, dir.y, dir.z, depth),
    )
}

/**
 * Kara parcalarinin yolu kamera belirgin sekilde kimildayana kadar saklanir;
 * her karede yeniden kirpip izdusurmek gereksiz.
 */
private class LandCache {
    private var key = ""
    private var path = Path()

    fun pathFor(camera: SkyCamera, shapes: ShapeSet): Path {
        val next = buildString {
            append((camera.lat * 400).roundToInt())
            append(',')
            append((camera.lon * 400).roundToInt())
            append(',')
            append((camera.azimuthDeg * 4).roundToInt())
            append(',')
            append(camera.altitudeKm.roundToInt())
            append(',')
            append(camera.width.roundToInt())
            append('x')
            append(camera.height.roundToInt())
        }
        if (next != key) {
            path = landPath(camera, shapes)
            key = next
        }
        return path
    }
}

/** Sabit yildiz alani: bir kez uretilir, dunyayla birlikte doner. */
private class StarField(count: Int) {
    private val xyz = DoubleArray(count * 3)
    private val magnitude = FloatArray(count)

    init {
        val random = Random(20240815)
        for (i in 0 until count) {
            // Kure uzerinde duzgun dagilim.
            val z = random.nextDouble(-1.0, 1.0)
            val a = random.nextDouble(0.0, Math.PI * 2)
            val r = kotlin.math.sqrt(1 - z * z)
            xyz[i * 3] = r * cos(a)
            xyz[i * 3 + 1] = r * sin(a)
            xyz[i * 3 + 2] = z
            magnitude[i] = random.nextFloat().let { it * it }
        }
    }

    fun draw(scope: DrawScope, camera: SkyCamera, night: Float) {
        val minUp = -sin(camera.horizonDip)
        for (i in 0 until magnitude.size) {
            val x = xyz[i * 3]
            val y = xyz[i * 3 + 1]
            val z = xyz[i * 3 + 2]
            if (x * camera.upX + y * camera.upY + z * camera.upZ < minUp) continue
            val depth = camera.depth(x, y, z)
            if (depth <= 1e-6) continue
            val sx = camera.screenX(x, y, z, depth)
            val sy = camera.screenY(x, y, z, depth)
            if (sx < 0f || sx > scope.size.width || sy < 0f || sy > scope.size.height) continue
            val alpha = night * (0.25f + 0.75f * magnitude[i])
            scope.drawCircle(
                color = Color.White.copy(alpha = alpha),
                radius = scope.size.height * (0.0009f + 0.0013f * magnitude[i]),
                center = Offset(sx, sy),
            )
        }
    }
}

/**
 * Ucagin altindaki bulut tabakasi.
 *
 * Her puf, kameranin yatay duzleminde (sag, ileri) km cinsinden durur; ucak
 * ilerledikce bu duzlem kayar, uzaktakiler yavas, yakindakiler hizli gecer —
 * paralaks kendiliginden dogar.
 */
private class CloudField(count: Int) {

    /** Her bulut, birkac kucuk pufun ust uste binmesinden olusur. */
    private val puffsPerCloud = 5

    private val u = DoubleArray(count * puffsPerCloud)
    private val v = DoubleArray(count * puffsPerCloud)
    private val radius = DoubleArray(count * puffsPerCloud)
    private val lift = DoubleArray(count * puffsPerCloud)

    init {
        val random = Random(770315)
        var i = 0
        for (cloud in 0 until count) {
            val cu = random.nextDouble(0.0, CLOUD_SPAN_U)
            val cv = random.nextDouble(0.0, CLOUD_SPAN_V)
            val scale = random.nextDouble(0.55, 1.9)
            val cz = random.nextDouble(-0.7, 1.1)
            for (puff in 0 until puffsPerCloud) {
                u[i] = cu + random.nextDouble(-2.6, 2.6) * scale
                v[i] = cv + random.nextDouble(-2.0, 2.0) * scale
                radius[i] = random.nextDouble(0.55, 1.7) * scale
                lift[i] = cz + random.nextDouble(-0.15, 0.35) * scale
                i++
            }
        }
    }

    fun draw(scope: DrawScope, camera: SkyCamera, palette: SkyPalette, time: Float) {
        // Ucagin bakis cercevesindeki kaymasi: burun yonu kamera acisiyla arasindaki fark.
        val relative = Math.toRadians(VIEW_OFFSET_DEG)
        val driftU = -GROUND_SPEED_KM_S * sin(-relative)
        val driftV = -GROUND_SPEED_KM_S * cos(-relative)

        for (i in u.indices) {
            var pu = (u[i] + driftU * time) % CLOUD_SPAN_U
            if (pu < 0) pu += CLOUD_SPAN_U
            pu -= CLOUD_SPAN_U / 2
            var pv = (v[i] + driftV * time) % CLOUD_SPAN_V
            if (pv < 0) pv += CLOUD_SPAN_V
            pv += 3.0

            val distance = hypot(pu, pv)
            if (distance < 2.0 || distance > camera.horizonKm) continue

            // Dunyanin egrilgi: uzaktaki bulut alcalir.
            val drop = distance * distance / (2 * EARTH_RADIUS_KM)
            val dz = CLOUD_ALT_KM + lift[i] - camera.altitudeKm - drop
            val elevation = atan2(dz, distance)
            val bear = camera.azimuthRad + atan2(pu, pv)
            val dir = camera.direction(elevation, bear)

            val depth = camera.depth(dir[0], dir[1], dir[2])
            if (depth <= 1e-6) continue
            val x = camera.screenX(dir[0], dir[1], dir[2], depth)
            val y = camera.screenY(dir[0], dir[1], dir[2], depth)

            val slant = hypot(distance, dz)
            val screenR = (camera.focal * radius[i] / slant).toFloat()
            if (screenR < 0.7f) continue
            if (x < -screenR * 2 || x > scope.size.width + screenR * 2) continue
            if (y < -screenR * 2 || y > scope.size.height + screenR * 2) continue

            // Uzaklastikca puslanip ufka karisirlar.
            val fade = (1.0 - distance / camera.horizonKm).coerceIn(0.0, 1.0).toFloat()
            val alpha = (0.16f + 0.42f * fade) * (screenR / (screenR + 3f))
            scope.drawCircle(
                color = palette.cloudShade.copy(alpha = alpha * 0.75f),
                radius = screenR,
                center = Offset(x, y + screenR * 0.16f),
            )
            scope.drawCircle(
                color = palette.cloudLit.copy(alpha = alpha),
                radius = screenR * 0.78f,
                center = Offset(x, y - screenR * 0.22f),
            )
        }
    }
}
