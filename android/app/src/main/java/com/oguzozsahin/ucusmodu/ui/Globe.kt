package com.oguzozsahin.ucusmodu.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.data.Airport
import com.oguzozsahin.ucusmodu.data.CoastDetail
import com.oguzozsahin.ucusmodu.data.CoastlineShapes
import com.oguzozsahin.ucusmodu.data.Coastlines
import com.oguzozsahin.ucusmodu.data.LatLon
import com.oguzozsahin.ucusmodu.data.Route
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.PlexMono
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val ROUTE_STEPS = 240
private const val TRANSITION_MS = 760

/** Bu yakinlastirmadan sonra ince kiyi seridine gecilir. */
private const val FINE_DETAIL_ZOOM = 2.2f

/** Ayni anda kurede en fazla kac yer isaretlenir. */
private const val MAX_MARKERS = 90

/** Bunlarin kacinin adi yazilir. */
private const val MAX_LABELS = 20

/**
 * Kameranin bakis yonunden turetilen taban: dogu, kuzey ve yukari birim vektorleri.
 * Noktalari izdusurmek icin trigonometri degil, yalnizca ic carpim gerekir.
 */
private class Projection(
    centerLat: Double,
    centerLon: Double,
    val cx: Float,
    val cy: Float,
    val r: Float,
) {
    private val phi = Math.toRadians(centerLat)
    private val lambda = Math.toRadians(centerLon)

    val eastX = (-sin(lambda)).toFloat()
    val eastY = cos(lambda).toFloat()
    val eastZ = 0f

    val northX = (-sin(phi) * cos(lambda)).toFloat()
    val northY = (-sin(phi) * sin(lambda)).toFloat()
    val northZ = cos(phi).toFloat()

    val upX = (cos(phi) * cos(lambda)).toFloat()
    val upY = (cos(phi) * sin(lambda)).toFloat()
    val upZ = sin(phi).toFloat()

    fun screenX(x: Float, y: Float, z: Float) = cx + r * (x * eastX + y * eastY + z * eastZ)
    fun screenY(x: Float, y: Float, z: Float) = cy - r * (x * northX + y * northY + z * northZ)
    fun visible(x: Float, y: Float, z: Float) = x * upX + y * upY + z * upZ >= 0f

    fun at(point: LatLon): ProjectedPoint {
        val p = Math.toRadians(point.lat)
        val l = Math.toRadians(point.lon)
        val x = (cos(p) * cos(l)).toFloat()
        val y = (cos(p) * sin(l)).toFloat()
        val z = sin(p).toFloat()
        return ProjectedPoint(screenX(x, y, z), screenY(x, y, z), visible(x, y, z))
    }
}

private class ProjectedPoint(val x: Float, val y: Float, val visible: Boolean)

/**
 * Ortografik kure: kiyi seritleri, paralel/meridyen agi, buyuk daire rotasi ve ucak.
 *
 * [interactive] acikken kure surukleyerek dondurulur, cimdikleyerek yakinlastirilir;
 * bu durumda kamera rotayi takip etmeyi birakir ve [state] uzerinden geri ortalanir.
 */
@Composable
fun GlobeMap(
    route: Route,
    fraction: Float,
    colors: FlightColors,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
    state: GlobeState? = null,
    places: List<Airport> = emptyList(),
    highlighted: Set<String> = emptySet(),
    onPlaceTap: ((Airport) -> Unit)? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val context = LocalContext.current
    // Isaretcilerin ekran konumu, dokunusu eslestirmek icin cizim sirasinda yazilir.
    val markers = remember { MarkerHits() }
    val zoom = state?.zoom ?: 1f

    // Rota noktalari yalnizca rota degisince yeniden hesaplanir.
    val routePoints = remember(route.key) {
        List(ROUTE_STEPS + 1) { i -> route.pointAt(i.toDouble() / ROUTE_STEPS) }
    }
    val graticule = remember { buildGraticule() }

    // Kiyi seritleri: kaba katman hemen, ince katman yakinlasinca yuklenir.
    var coarse by remember { mutableStateOf<CoastlineShapes?>(null) }
    var fine by remember { mutableStateOf<CoastlineShapes?>(null) }
    val wantsFine = (state?.zoom ?: 1f) >= FINE_DETAIL_ZOOM

    LaunchedEffect(Unit) {
        coarse = Coastlines.load(context, CoastDetail.COARSE)
    }
    LaunchedEffect(wantsFine) {
        if (wantsFine && fine == null) fine = Coastlines.load(context, CoastDetail.FINE)
    }
    val coastlines = if (wantsFine) fine ?: coarse else coarse

    // Kamera: rota degisince kure yeni merkeze donerek gider, zaplamaz.
    val cameraFrom = remember { mutableStateOf(route.center) }
    val cameraTo = remember { mutableStateOf(route.center) }
    val spin = remember { Animatable(1f) }
    val routeCamera by remember {
        derivedStateOf { lerpCenter(cameraFrom.value, cameraTo.value, spin.value) }
    }
    // Yeni rota bastan cizilerek belirir.
    val reveal = remember { Animatable(1f) }

    LaunchedEffect(route.key) {
        if (cameraTo.value == route.center) return@LaunchedEffect
        cameraFrom.value = routeCamera
        cameraTo.value = route.center
        spin.snapTo(0f)
        reveal.snapTo(0f)
        launch { reveal.animateTo(1f, tween(TRANSITION_MS, easing = FastOutSlowInEasing)) }
        spin.animateTo(1f, tween(TRANSITION_MS, easing = FastOutSlowInEasing))
    }

    // Ucagin adim adim degil, akici ilerlemesi icin araya animasyon konur.
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "ucus-orani",
    )

    val gestures = if (!interactive || state == null) {
        Modifier
    } else {
        Modifier
            .pointerInput(Unit) {
                val reach = 26.dp.toPx()
                detectTapGestures(
                    onDoubleTap = { state.toggleZoom() },
                    onTap = { offset ->
                        markers.nearest(offset.x, offset.y, reach)?.let { onPlaceTap?.invoke(it) }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoomChange, _ ->
                    val radius = min(size.width, size.height) * 0.40f * state.zoom
                    if (radius > 1f) {
                        state.rotateBy(
                            deltaLat = Math.toDegrees((pan.y / radius).toDouble()).toFloat(),
                            deltaLon = -Math.toDegrees((pan.x / radius).toDouble()).toFloat(),
                        )
                    }
                    state.zoomBy(zoomChange)
                }
            }
    }

    Canvas(modifier.then(gestures)) {
        val baseRadius = min(size.width, size.height) * 0.40f
        val r = baseRadius * zoom
        val camera = state?.center ?: routeCamera
        val proj = Projection(
            centerLat = camera.lat.coerceIn(-89.0, 89.0),
            centerLon = camera.lon,
            cx = size.width / 2f,
            cy = size.height / 2f - 8.dp.toPx(),
            r = r,
        )
        val center = Offset(proj.cx, proj.cy)

        drawRect(color = colors.space)

        // kure govdesi
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(colors.globeA, colors.globeB),
                center = Offset(proj.cx - r * 0.35f, proj.cy - r * 0.40f),
                radius = r * 1.45f,
            ),
            radius = r,
            center = center,
        )

        clipPath(Path().apply { addOval(Rect(center, r)) }) {
            // paralel / meridyen agi
            for (line in graticule) {
                drawPath(
                    path = proj.pathOf(line),
                    color = colors.grat,
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            drawPath(
                path = proj.pathOf(equator()),
                color = colors.limb,
                style = Stroke(width = 1.4.dp.toPx()),
            )

            // kiyi seritleri
            coastlines?.let { shapes ->
                val path = proj.coastPath(shapes)
                drawPath(
                    path = path,
                    color = colors.land,
                    style = Stroke(
                        width = 1.3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        // kure kenari
        drawCircle(
            color = colors.limb,
            radius = r,
            center = center,
            style = Stroke(width = 1.4.dp.toPx()),
        )

        // rota: once kalan (kesikli), sonra kat edilen (parlak)
        val shown = (reveal.value * ROUTE_STEPS).toInt().coerceIn(0, ROUTE_STEPS)
        val cut = (animatedFraction * ROUTE_STEPS).toInt().coerceIn(0, shown)

        if (cut < shown) {
            drawPath(
                path = proj.pathOf(routePoints.subList(maxOf(0, cut - 1), shown + 1)),
                color = colors.muted,
                style = Stroke(
                    width = 1.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(4.dp.toPx(), 5.dp.toPx()),
                        0f,
                    ),
                ),
            )
        }

        val flown = proj.pathOf(routePoints.subList(0, cut + 1))
        // isik halesi: ayni yol artan kalinlikta, dusuk saydamlikla
        drawPath(flown, colors.amber.copy(alpha = 0.10f), style = Stroke(9.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(flown, colors.amber.copy(alpha = 0.18f), style = Stroke(5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(flown, colors.amber, style = Stroke(2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        // kalkis ve varis noktalari
        drawEndpoint(proj, routePoints.first(), route.origin.code, colors, textMeasurer, reveal.value)
        drawEndpoint(proj, routePoints.last(), route.dest.code, colors, textMeasurer, reveal.value)

        // yerler
        markers.items = if (places.isEmpty()) {
            emptyList()
        } else {
            drawPlaces(proj, places, highlighted, zoom, colors, textMeasurer)
        }

        // ucak
        val t = (animatedFraction * reveal.value).coerceAtMost(0.9999f).toDouble()
        val here = route.pointAt(t)
        val ahead = route.pointAt(min(1.0, t + 0.004))
        val p1 = proj.at(here)
        val p2 = proj.at(ahead)
        if (p1.visible) {
            val angle = Math.toDegrees(
                atan2((p2.y - p1.y).toDouble(), (p2.x - p1.x).toDouble()),
            ).toFloat()
            val plane = planePath(1.dp.toPx())
            withTransform({
                translate(p1.x, p1.y)
                rotate(angle, Offset.Zero)
            }) {
                drawPath(plane, colors.amber.copy(alpha = 0.22f), style = Stroke(6.dp.toPx()))
                drawPath(plane, colors.amber)
            }
        }
    }
}

/** Cizim sirasinda doldurulan, dokunus eslestirmede kullanilan isaretci listesi. */
private class MarkerHits {
    var items: List<Marker> = emptyList()

    fun nearest(x: Float, y: Float, reach: Float): Airport? {
        var best: Marker? = null
        var bestDistance = reach * reach
        for (marker in items) {
            val dx = marker.x - x
            val dy = marker.y - y
            val distance = dx * dx + dy * dy
            if (distance <= bestDistance) {
                bestDistance = distance
                best = marker
            }
        }
        return best?.place
    }
}

private class Marker(val x: Float, val y: Float, val place: Airport)

/**
 * Yerleri onem sirasina gore isaretler: uzaktan yalnizca buyuk havalimanlari,
 * yakinlastikca kucukleri de gorunur. Secili olanlar her zaman cizilir.
 */
private fun DrawScope.drawPlaces(
    proj: Projection,
    places: List<Airport>,
    highlighted: Set<String>,
    zoom: Float,
    colors: FlightColors,
    measurer: androidx.compose.ui.text.TextMeasurer,
): List<Marker> {
    val maxRank = when {
        zoom < 1.7f -> 0
        zoom < 3f -> 1
        else -> 2
    }
    val dot = 2.4.dp.toPx()
    val labelGap = 5.dp.toPx()
    val shown = ArrayList<Marker>(MAX_MARKERS)

    for (place in places) {
        val chosen = place.code in highlighted
        if (!chosen) {
            if (place.rank > maxRank) continue
            if (shown.size >= MAX_MARKERS) continue
        }
        val p = proj.at(LatLon(place.lat, place.lon))
        if (!p.visible) continue
        if (p.x < 0f || p.y < 0f || p.x > size.width || p.y > size.height) continue

        val color = if (chosen) colors.amber else colors.teal
        drawCircle(color, radius = if (chosen) dot * 1.6f else dot, center = Offset(p.x, p.y))
        if (chosen) {
            drawCircle(
                color.copy(alpha = 0.35f),
                radius = dot * 3.2f,
                center = Offset(p.x, p.y),
                style = Stroke(1.4.dp.toPx()),
            )
        }
        shown += Marker(p.x, p.y, place)
    }

    // Adlar yalnizca ilk birkac yer icin; kalabalik olmasin.
    var labelled = 0
    for (marker in shown) {
        val chosen = marker.place.code in highlighted
        if (!chosen && labelled >= MAX_LABELS) continue
        if (!chosen && zoom < 1.35f && marker.place.rank > 0) continue
        labelled++
        val layout = measurer.measure(
            AnnotatedString(marker.place.city),
            style = TextStyle(
                fontFamily = PlexMono,
                fontWeight = if (chosen) FontWeight.Medium else FontWeight.Normal,
                fontSize = if (chosen) 12.sp else 10.sp,
                color = if (chosen) colors.amber else colors.ink.copy(alpha = 0.72f),
            ),
        )
        drawText(
            textLayoutResult = layout,
            topLeft = Offset(marker.x + labelGap, marker.y - layout.size.height / 2f),
        )
    }
    return shown
}

private fun DrawScope.drawEndpoint(
    proj: Projection,
    point: LatLon,
    code: String,
    colors: FlightColors,
    measurer: androidx.compose.ui.text.TextMeasurer,
    alpha: Float,
) {
    val q = proj.at(point)
    if (!q.visible || alpha <= 0.01f) return
    val center = Offset(q.x, q.y)
    val teal = colors.teal.copy(alpha = alpha)
    drawCircle(teal, radius = 4.5.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
    drawCircle(teal, radius = 1.6.dp.toPx(), center = center)

    val layout = measurer.measure(
        AnnotatedString(code),
        style = TextStyle(
            fontFamily = PlexMono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = colors.ink.copy(alpha = alpha),
        ),
    )
    drawText(
        textLayoutResult = layout,
        topLeft = Offset(
            q.x - layout.size.width / 2f,
            q.y - 11.dp.toPx() - layout.size.height,
        ),
    )
}

/** Ucak siluetinin yerel koordinatlari (web surumuyle ayni oranlar). */
private fun planePath(unit: Float): Path = Path().apply {
    moveTo(11f * unit, 0f)
    lineTo(-5f * unit, 7f * unit)
    lineTo(-1.5f * unit, 0f)
    lineTo(-5f * unit, -7f * unit)
    close()
}

/** Kurenin arkasina dusen parcalari kopararak yol olusturur. */
private fun Projection.pathOf(points: List<LatLon>): Path {
    val path = Path()
    var drawing = false
    for (p in points) {
        val q = at(p)
        if (!q.visible) {
            drawing = false
            continue
        }
        if (drawing) {
            path.lineTo(q.x, q.y)
        } else {
            path.moveTo(q.x, q.y)
            drawing = true
        }
    }
    return path
}

/** Tum kiyi seritlerini tek bir yola toplar; arkada kalan parcalar elenir. */
private fun Projection.coastPath(shapes: CoastlineShapes): Path {
    val path = Path()
    for (shape in shapes.shapes) {
        if (shape.isHidden(upX, upY, upZ)) continue
        val xyz = shape.xyz
        var drawing = false
        var i = 0
        while (i < xyz.size) {
            val x = xyz[i]
            val y = xyz[i + 1]
            val z = xyz[i + 2]
            if (!visible(x, y, z)) {
                drawing = false
            } else {
                val sx = screenX(x, y, z)
                val sy = screenY(x, y, z)
                if (drawing) {
                    path.lineTo(sx, sy)
                } else {
                    path.moveTo(sx, sy)
                    drawing = true
                }
            }
            i += 3
        }
    }
    return path
}

/** Kamera gecisi: boylamda kisa yoldan donerek yumusak bir kure hareketi. */
private fun lerpCenter(from: LatLon, to: LatLon, t: Float): LatLon {
    var dLon = to.lon - from.lon
    if (dLon > 180.0) dLon -= 360.0
    if (dLon < -180.0) dLon += 360.0
    return LatLon(
        lat = from.lat + (to.lat - from.lat) * t,
        lon = from.lon + dLon * t,
    )
}

private fun buildGraticule(): List<List<LatLon>> {
    val lines = mutableListOf<List<LatLon>>()
    var lat = -80
    while (lat <= 80) {
        val row = mutableListOf<LatLon>()
        var lon = -180
        while (lon <= 180) {
            row += LatLon(lat.toDouble(), lon.toDouble())
            lon += 3
        }
        lines += row
        lat += 20
    }
    var lon = -180
    while (lon < 180) {
        val col = mutableListOf<LatLon>()
        var la = -90
        while (la <= 90) {
            col += LatLon(la.toDouble(), lon.toDouble())
            la += 3
        }
        lines += col
        lon += 20
    }
    return lines
}

private fun equator(): List<LatLon> {
    val pts = mutableListOf<LatLon>()
    var lon = -180
    while (lon <= 180) {
        pts += LatLon(0.0, lon.toDouble())
        lon += 2
    }
    return pts
}
