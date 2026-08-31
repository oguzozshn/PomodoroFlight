package com.oguzozsahin.ucusmodu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
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
import kotlin.math.atan2
import kotlin.math.min

private const val ROUTE_STEPS = 240

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

    Canvas(modifier) {
        val r = min(size.width, size.height) * 0.40f
        val proj = Projection(
            centerLat = route.center.lat,
            centerLon = route.center.lon,
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
        val cut = (fraction * ROUTE_STEPS).toInt().coerceIn(0, ROUTE_STEPS)

        if (cut < ROUTE_STEPS) {
            drawPath(
                path = proj.pathOf(routePoints.subList(maxOf(0, cut - 1), routePoints.size)),
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
        drawEndpoint(proj, routePoints.first(), route.origin.code, colors, textMeasurer)
        drawEndpoint(proj, routePoints.last(), route.dest.code, colors, textMeasurer)

        // ucak
        val t = fraction.coerceAtMost(0.9999f).toDouble()
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
) {
    val q = proj.at(point)
    if (!q.visible) return
    val center = Offset(q.x, q.y)
    drawCircle(colors.teal, radius = 4.5.dp.toPx(), center = center, style = Stroke(2.dp.toPx()))
    drawCircle(colors.teal, radius = 1.6.dp.toPx(), center = center)

    val layout = measurer.measure(
        AnnotatedString(code),
        style = TextStyle(
            fontFamily = PlexMono,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = colors.ink,
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

private fun equator(): List<LatLon> {
    val pts = mutableListOf<LatLon>()
    var lon = -180
    while (lon <= 180) {
        pts += LatLon(0.0, lon.toDouble())
        lon += 2
    }
    return pts
}
