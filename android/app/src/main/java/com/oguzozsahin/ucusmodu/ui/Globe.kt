package com.oguzozsahin.ucusmodu.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
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
import com.oguzozsahin.ucusmodu.data.WorldAsset
import com.oguzozsahin.ucusmodu.data.ShapeSet
import com.oguzozsahin.ucusmodu.data.WorldShapes
import com.oguzozsahin.ucusmodu.data.LatLon
import com.oguzozsahin.ucusmodu.data.Route
import com.oguzozsahin.ucusmodu.data.Sun
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.PlexMono
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

private const val ROUTE_STEPS = 240
private const val TRANSITION_MS = 760

/** Bu yakinlastirmadan sonra ince kiyi seridine gecilir. */
private const val FINE_DETAIL_ZOOM = 2.2f

/** Ayni anda kurede en fazla kac yer isaretlenir. */
private const val MAX_MARKERS = 140

/** Bunlarin kacinin adi yazilir. */
private const val MAX_LABELS = 22

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
    /** Tamamlanmis ucuslarin rota anahtarlari; kurede soluk iz birakirlar. */
    trails: List<String> = emptyList(),
    onPlaceTap: ((Airport) -> Unit)? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val context = LocalContext.current
    // Isaretcilerin ekran konumu, dokunusu eslestirmek icin cizim sirasinda yazilir.
    val markers = remember { MarkerHits() }
    val momentum = remember { MomentumHolder() }
    val gestureScope = rememberCoroutineScope()
    val zoom = state?.zoom ?: 1f

    // Rota noktalari yalnizca rota degisince yeniden hesaplanir.
    val routePoints = remember(route.key) {
        List(ROUTE_STEPS + 1) { i -> route.pointAt(i.toDouble() / ROUTE_STEPS) }
    }
    val graticule = remember { buildGraticule() }

    // Tamamlanan ucuslarin izleri
    val trailPoints = remember(trails) {
        trails.asSequence()
            .distinct()
            .take(24)
            .mapNotNull { Route.of(it) }
            .map { route -> List(64) { i -> route.pointAt(i / 63.0) } }
            .toList()
    }

    // Gunes konumu dakikada bir tazelenir; gunduz/gece siniri buna gore cizilir.
    val sun by produceState(initialValue = Sun.subsolarPoint(System.currentTimeMillis())) {
        while (true) {
            value = Sun.subsolarPoint(System.currentTimeMillis())
            delay(60_000L)
        }
    }

    // Kara parcalari: kaba katman hemen, ince katman yakinlasinca yuklenir.
    var coarse by remember { mutableStateOf<ShapeSet?>(null) }
    var fine by remember { mutableStateOf<ShapeSet?>(null) }
    var lakes by remember { mutableStateOf<ShapeSet?>(null) }
    val wantsFine = (state?.zoom ?: 1f) >= FINE_DETAIL_ZOOM

    LaunchedEffect(Unit) {
        coarse = WorldShapes.load(context, WorldAsset.LAND_COARSE)
        lakes = WorldShapes.load(context, WorldAsset.LAKES)
    }
    LaunchedEffect(wantsFine) {
        if (wantsFine && fine == null) fine = WorldShapes.load(context, WorldAsset.LAND_FINE)
    }
    val landShapes = if (wantsFine) fine ?: coarse else coarse

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
                // Parmak kalkinca kure sonumlenerek donmeye devam eder.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    momentum.job?.cancel()
                    state.stopMomentum()
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.changes.none { it.pressed }) break
                    }
                    momentum.job = gestureScope.launch { state.flingMomentum() }
                }
            }
            .pointerInput(Unit) {
                val reach = 26.dp.toPx()
                detectTapGestures(
                    onDoubleTap = { offset ->
                        // Cift dokunus, kameranin merkezine degil, dokunulan yere yaklasir.
                        val target = markers.locate(offset.x, offset.y)
                        if (target == null) {
                            state.toggleZoom()
                        } else {
                            val nextZoom = if (state.zoom < 2f) 3.5f else GLOBE_MIN_ZOOM
                            gestureScope.launch {
                                state.flyTo(target.lat, target.lon, targetZoom = nextZoom, durationMs = 480)
                            }
                        }
                    },
                    onTap = { offset ->
                        markers.nearest(offset.x, offset.y, reach)?.let { onPlaceTap?.invoke(it) }
                    },
                )
            }
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoomChange, _ ->
                    val radius = min(size.width, size.height) * 0.40f * state.zoom
                    if (radius > 1f && (pan.x != 0f || pan.y != 0f)) {
                        state.rotateBy(
                            deltaLat = Math.toDegrees((pan.y / radius).toDouble()).toFloat(),
                            deltaLon = -Math.toDegrees((pan.x / radius).toDouble()).toFloat(),
                        )
                    }
                    zoomAnchored(state, markers, centroid.x, centroid.y, zoomChange)
                }
            }
            .pointerInput(Unit) {
                // Fare tekerlegi. Olay en bastaki asamada yakalanir; Main asamasina
                // birakilirsa baska bir bilesen tuketip yutabiliyor.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type != PointerEventType.Scroll) continue
                        var delta = 0f
                        var position = Offset.Zero
                        for (change in event.changes) {
                            // Bazi girdilerde tekerlek yatay eksene dusuyor.
                            val amount = if (change.scrollDelta.y != 0f) {
                                change.scrollDelta.y
                            } else {
                                change.scrollDelta.x
                            }
                            if (amount != 0f) {
                                delta += amount
                                position = change.position
                            }
                        }
                        if (delta == 0f) continue
                        val factor = exp(-delta.coerceIn(-4f, 4f) * 0.22f)
                        zoomAnchored(state, markers, position.x, position.y, factor)
                        event.changes.forEach { it.consume() }
                    }
                }
            }
    }

    Canvas(modifier.then(gestures)) {
        val baseRadius = min(size.width, size.height) * 0.40f
        val r = baseRadius * zoom
        val camera = state?.center ?: routeCamera
        val proj = GlobeCamera(
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
            // kara parcalari: dolgu, sonra goller, sonra kiyi cizgisi
            landShapes?.let { shapes ->
                drawPath(proj.areaPath(shapes), colors.land)
            }
            lakes?.let { shapes ->
                drawPath(proj.areaPath(shapes), colors.lake)
            }
            landShapes?.let { shapes ->
                drawPath(
                    path = proj.outlinePath(shapes),
                    color = colors.coast,
                    style = Stroke(
                        width = 1.1.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }

            // paralel / meridyen agi karanin uzerinde, soluk
            for (line in graticule) {
                drawPath(
                    path = proj.pathOf(line),
                    color = colors.grat.copy(alpha = 0.5f),
                    style = Stroke(width = 1.dp.toPx()),
                )
            }
            drawPath(
                path = proj.pathOf(equator()),
                color = colors.limb.copy(alpha = 0.55f),
                style = Stroke(width = 1.2.dp.toPx()),
            )

            // gunduz/gece: dunyanin karanlik yarisi
            drawNight(proj, sun, colors)

            // tamamlanan ucuslarin izleri
            for (points in trailPoints) {
                drawPath(
                    path = proj.pathOf(points),
                    color = colors.amber.copy(alpha = 0.20f),
                    style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round),
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
        markers.setCamera(proj.cx, proj.cy, proj.r, camera.lat, camera.lon)
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

/**
 * Cizim sirasinda doldurulan kare bilgisi: isaretcilerin ekran konumu ve
 * dokunulan noktayi cografi koordinata cevirmek icin kamera.
 */
private class MarkerHits {
    var items: List<Marker> = emptyList()

    /** Son karenin kamerasi; ekran-koordinat cevrimi onun uzerinden. */
    private var camera: GlobeCamera? = null

    fun setCamera(cx: Float, cy: Float, radius: Float, lat: Double, lon: Double) {
        camera = GlobeCamera(lat, lon, cx, cy, radius)
    }

    /** Ekrandaki noktanin kure uzerindeki karsiligi; kurenin disiysa null. */
    fun locate(x: Float, y: Float, radiusScale: Float = 1f): LatLon? =
        camera?.locate(x, y, radiusScale)

    /**
     * Olcek [radiusScale] katina ciktiginda [anchor] koordinatini hala (x, y)
     * pikselinde tutan kamera merkezi; boyle bir kamera yoksa null.
     */
    fun solveCamera(anchor: LatLon, x: Float, y: Float, radiusScale: Float): LatLon? =
        camera?.solveCamera(anchor, x, y, radiusScale)

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

/** Suren atalet animasyonunu tutar; yeni dokunusta iptal edilir. */
private class MomentumHolder {
    var job: Job? = null
}

/** Isaretlenmeye aday yer: ekran konumu ve ekranin ortasina uzakligi. */
private class Candidate(
    val x: Float,
    val y: Float,
    val place: Airport,
    val chosen: Boolean,
    val distanceSq: Float,
)

/**
 * Yakinlastirmayi ekranin ortasina degil, parmagin (ya da imlecin) altindaki
 * noktaya sabitler: once orada hangi koordinatin durdugu bulunur, sonra yeni
 * olcekte ayni koordinati ayni piksele getiren kamera dogrudan cozulur.
 */
private fun zoomAnchored(
    state: GlobeState,
    markers: MarkerHits,
    x: Float,
    y: Float,
    factor: Float,
) {
    if (factor == 1f) return
    val anchor = markers.locate(x, y)
    val zoomBefore = state.zoom
    state.zoomBy(factor)
    val applied = state.zoom / zoomBefore
    if (applied == 1f || anchor == null) return

    // Cozum yoksa (uzaklasirken nokta kurenin disina dusuyorsa) merkeze gore
    // yakinlastirmakla yetinilir.
    val camera = markers.solveCamera(anchor, x, y, applied) ?: return
    state.moveTo(camera.lat, camera.lon)
}

/** Terminator yayini ornekleme adimi. */
private const val TERMINATOR_STEPS = 180
private const val LIMB_STEPS = 96

/**
 * Dunyanin gece yarisini golgeler. Gece bolgesinin siniri iki yaydan olusur:
 * terminatorun gorunen parcasi ve onu kapatan ufuk yayi. Ufuk yayi kurenin
 * disina biraz tasirilir; kirpma zaten daireye yapildigi icin kenarda ince
 * bir serit kalmaz.
 */
private fun DrawScope.drawNight(proj: GlobeCamera, sun: LatLon, colors: FlightColors) {
    val p = Math.toRadians(sun.lat)
    val l = Math.toRadians(sun.lon)
    val sx = (cos(p) * cos(l)).toFloat()
    val sy = (cos(p) * sin(l)).toFloat()
    val sz = sin(p).toFloat()

    val sunUp = sx * proj.upX + sy * proj.upY + sz * proj.upZ
    if (sunUp >= 0.99999f) return
    val center = Offset(proj.cx, proj.cy)
    if (sunUp <= -0.99999f) {
        drawCircle(colors.night, radius = proj.r, center = center)
        return
    }

    // terminator duzlemini geren iki dik birim vektor
    val refX: Float
    val refY: Float
    val refZ: Float
    if (kotlin.math.abs(sz) < 0.9f) {
        refX = 0f; refY = 0f; refZ = 1f
    } else {
        refX = 1f; refY = 0f; refZ = 0f
    }
    var ax = sy * refZ - sz * refY
    var ay = sz * refX - sx * refZ
    var az = sx * refY - sy * refX
    var length = kotlin.math.sqrt(ax * ax + ay * ay + az * az)
    if (length < 1e-6f) return
    ax /= length; ay /= length; az /= length

    var bx = sy * az - sz * ay
    var by = sz * ax - sx * az
    var bz = sx * ay - sy * ax
    length = kotlin.math.sqrt(bx * bx + by * by + bz * bz)
    if (length < 1e-6f) return
    bx /= length; by /= length; bz /= length

    val visible = BooleanArray(TERMINATOR_STEPS)
    for (i in 0 until TERMINATOR_STEPS) {
        val t = 2.0 * Math.PI * i / TERMINATOR_STEPS
        val c = cos(t).toFloat()
        val s = sin(t).toFloat()
        val vx = ax * c + bx * s
        val vy = ay * c + by * s
        val vz = az * c + bz * s
        visible[i] = proj.visible(vx, vy, vz)
    }

    var start = -1
    for (i in 0 until TERMINATOR_STEPS) {
        val previous = (i - 1 + TERMINATOR_STEPS) % TERMINATOR_STEPS
        if (visible[i] && !visible[previous]) {
            start = i
            break
        }
    }
    if (start < 0) {
        if (sunUp < 0f) drawCircle(colors.night, radius = proj.r, center = center)
        return
    }

    val path = Path()
    var firstX = 0f; var firstY = 0f; var firstZ = 0f
    var lastX = 0f; var lastY = 0f; var lastZ = 0f
    var count = 0
    var i = start
    while (visible[i % TERMINATOR_STEPS] && count <= TERMINATOR_STEPS) {
        val t = 2.0 * Math.PI * (i % TERMINATOR_STEPS) / TERMINATOR_STEPS
        val c = cos(t).toFloat()
        val s = sin(t).toFloat()
        val vx = ax * c + bx * s
        val vy = ay * c + by * s
        val vz = az * c + bz * s
        val screenX = proj.screenX(vx, vy, vz)
        val screenY = proj.screenY(vx, vy, vz)
        if (count == 0) {
            path.moveTo(screenX, screenY)
            firstX = vx; firstY = vy; firstZ = vz
        } else {
            path.lineTo(screenX, screenY)
        }
        lastX = vx; lastY = vy; lastZ = vz
        count++
        i++
    }
    if (count < 2) return

    // ufuk yayi: son noktadan ilk noktaya, gece tarafindan dolasarak
    val angleFirst = atan2(
        (firstX * proj.northX + firstY * proj.northY + firstZ * proj.northZ).toDouble(),
        (firstX * proj.eastX + firstY * proj.eastY + firstZ * proj.eastZ).toDouble(),
    )
    val angleLast = atan2(
        (lastX * proj.northX + lastY * proj.northY + lastZ * proj.northZ).toDouble(),
        (lastX * proj.eastX + lastY * proj.eastY + lastZ * proj.eastZ).toDouble(),
    )
    var sweep = (angleFirst - angleLast).mod(2.0 * Math.PI)
    val mid = angleLast + sweep / 2.0
    val midX = proj.eastX * cos(mid).toFloat() + proj.northX * sin(mid).toFloat()
    val midY = proj.eastY * cos(mid).toFloat() + proj.northY * sin(mid).toFloat()
    val midZ = proj.eastZ * cos(mid).toFloat() + proj.northZ * sin(mid).toFloat()
    if (midX * sx + midY * sy + midZ * sz > 0f) sweep -= 2.0 * Math.PI

    val outer = proj.r * 1.02f
    for (k in 1..LIMB_STEPS) {
        val angle = angleLast + sweep * k / LIMB_STEPS
        path.lineTo(
            proj.cx + outer * cos(angle).toFloat(),
            proj.cy - outer * sin(angle).toFloat(),
        )
    }
    path.close()
    drawPath(path, colors.night)
}

private fun DrawScope.drawPlaces(
    proj: GlobeCamera,
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
    val midX = size.width / 2f
    val midY = size.height / 2f

    // Once ekrana dusen tum adaylar toplanir; butce sonra ekranin ortasina
    // en yakin olanlara dagitilir. Aksi halde bütce veri setinin sirasina
    // gore harcanir ve baktiginiz sehir isaretlenmeden kalir.
    val candidates = ArrayList<Candidate>(256)
    for (place in places) {
        val chosen = place.code in highlighted
        if (!chosen && place.rank > maxRank) continue
        val p = proj.at(LatLon(place.lat, place.lon))
        if (!p.visible) continue
        if (p.x < 0f || p.y < 0f || p.x > size.width || p.y > size.height) continue
        val dx = p.x - midX
        val dy = p.y - midY
        candidates += Candidate(p.x, p.y, place, chosen, dx * dx + dy * dy)
    }
    candidates.sortWith(
        compareBy({ if (it.chosen) 0 else 1 }, { it.place.rank }, { it.distanceSq }),
    )

    val shown = ArrayList<Marker>(MAX_MARKERS)
    for (candidate in candidates) {
        if (!candidate.chosen && shown.size >= MAX_MARKERS) break
        val chosen = candidate.chosen
        val color = if (chosen) colors.amber else colors.teal
        val center = Offset(candidate.x, candidate.y)
        drawCircle(color, radius = if (chosen) dot * 1.6f else dot, center = center)
        if (chosen) {
            drawCircle(
                color.copy(alpha = 0.35f),
                radius = dot * 3.2f,
                center = center,
                style = Stroke(1.4.dp.toPx()),
            )
        }
        shown += Marker(candidate.x, candidate.y, candidate.place)
    }

    // Adlar yalnizca ilk birkac yer icin; kalabalik olmasin.
    var labelled = 0
    for (marker in shown) {
        val chosen = marker.place.code in highlighted
        if (!chosen && labelled >= MAX_LABELS) continue
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
    proj: GlobeCamera,
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
private fun GlobeCamera.pathOf(points: List<LatLon>): Path {
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

/**
 * Kara parcalarini doldurulabilir tek bir yola toplar. Kurenin arkasina dusen
 * noktalar ufka yapistirilir, aralarindaki bosluk da ufuk boyunca doldurulur;
 * boylece kenardan tasan kitalarin silueti ufku takip eder. Delikler even-odd
 * kuraliyla kendiliginden acilir.
 */
private fun GlobeCamera.areaPath(shapes: ShapeSet): Path {
    val path = Path()
    path.fillType = PathFillType.EvenOdd
    val twoPi = (2.0 * Math.PI).toFloat()
    for (shape in shapes.shapes) {
        if (shape.isHidden(upX, upY, upZ)) continue
        val xyz = shape.xyz
        var started = false
        var clampedAngle = Float.NaN
        var i = 0
        while (i < xyz.size) {
            val x = xyz[i]
            val y = xyz[i + 1]
            val z = xyz[i + 2]
            val east = x * eastX + y * eastY + z * eastZ
            val north = x * northX + y * northY + z * northZ

            if (visible(x, y, z)) {
                val sx = cx + r * east
                val sy = cy - r * north
                if (started) path.lineTo(sx, sy) else { path.moveTo(sx, sy); started = true }
                clampedAngle = Float.NaN
            } else {
                val length = kotlin.math.sqrt(east * east + north * north)
                if (length > 1e-6f) {
                    val angle = atan2(north.toDouble(), east.toDouble()).toFloat()
                    if (started && !clampedAngle.isNaN()) {
                        var delta = angle - clampedAngle
                        while (delta > Math.PI) delta -= twoPi
                        while (delta < -Math.PI) delta += twoPi
                        val steps = (kotlin.math.abs(delta) / 0.08f).toInt()
                        for (k in 1 until steps) {
                            val between = clampedAngle + delta * k / steps
                            path.lineTo(
                                cx + r * cos(between.toDouble()).toFloat(),
                                cy - r * sin(between.toDouble()).toFloat(),
                            )
                        }
                    }
                    val sx = cx + r * cos(angle.toDouble()).toFloat()
                    val sy = cy - r * sin(angle.toDouble()).toFloat()
                    if (started) path.lineTo(sx, sy) else { path.moveTo(sx, sy); started = true }
                    clampedAngle = angle
                }
            }
            i += 3
        }
        if (started) path.close()
    }
    return path
}

/** Kiyi cizgisi: yalnizca gorunen parcalar, ufuk boyunca cizgi cizilmez. */
private fun GlobeCamera.outlinePath(shapes: ShapeSet): Path {
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
