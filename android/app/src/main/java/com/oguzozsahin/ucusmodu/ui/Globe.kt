package com.oguzozsahin.ucusmodu.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.data.LatLon
import com.oguzozsahin.ucusmodu.data.Route
import com.oguzozsahin.ucusmodu.data.project
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.PlexMono
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.min

private const val ROUTE_STEPS = 240
private const val TRANSITION_MS = 760

/** Kurenin merkezi ve olcegi. */
private data class Projection(
    val centerLat: Double,
    val centerLon: Double,
    val cx: Float,
    val cy: Float,
    val r: Float,
)

/**
 * Ortografik kure: paralel/meridyen agi, buyuk daire rotasi ve rotadaki ucak.
 * Web surumundeki canvas ciziminin Compose karsiligi.
 */
@Composable
fun GlobeMap(
    route: Route,
    fraction: Float,
    colors: FlightColors,
    modifier: Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    // Rota noktalari yalnizca rota degisince yeniden hesaplanir.
    val routePoints = remember(route.key) {
        List(ROUTE_STEPS + 1) { i -> route.pointAt(i.toDouble() / ROUTE_STEPS) }
    }
    val graticule = remember { buildGraticule() }

    // Kamera: rota degisince kure yeni merkeze donerek gider, zaplamaz.
    val cameraFrom = remember { mutableStateOf(route.center) }
    val cameraTo = remember { mutableStateOf(route.center) }
    val spin = remember { Animatable(1f) }
    val camera by remember {
        derivedStateOf { lerpCenter(cameraFrom.value, cameraTo.value, spin.value) }
    }
    // Yeni rota bastan cizilerek belirir.
    val reveal = remember { Animatable(1f) }

    LaunchedEffect(route.key) {
        if (cameraTo.value == route.center) return@LaunchedEffect
        cameraFrom.value = camera
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

    Canvas(modifier) {
        val r = min(size.width, size.height) * 0.40f
        val proj = Projection(
            centerLat = camera.lat,
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

        // paralel / meridyen agi
        clipPath(Path().apply { addOval(Rect(center, r)) }) {
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

private fun Projection.at(p: LatLon) =
    project(p.lat, p.lon, centerLat, centerLon, cx, cy, r)

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
        if (drawing) path.lineTo(q.x, q.y) else {
            path.moveTo(q.x, q.y)
            drawing = true
        }
    }
    return path
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

private fun equator(): List<LatLon> {
    val pts = mutableListOf<LatLon>()
    var lon = -180
    while (lon <= 180) {
        pts += LatLon(0.0, lon.toDouble())
        lon += 2
    }
    return pts
}
