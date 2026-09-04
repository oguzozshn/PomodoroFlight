package com.oguzozsahin.ucusmodu.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.data.Ticket
import com.oguzozsahin.ucusmodu.data.formatDuration
import com.oguzozsahin.ucusmodu.data.formatNumber
import com.oguzozsahin.ucusmodu.data.formatTimeOfDay
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.LocalFlightColors
import com.oguzozsahin.ucusmodu.ui.theme.labelStyle
import com.oguzozsahin.ucusmodu.ui.theme.monoStyle
import kotlinx.coroutines.launch

/** Yazicinin kagidi besleme suresi. */
private const val PRINT_MS = 1900

/** Bu orandan sonra birakilirsa bilet kopar, altinda geri yapisir. */
private const val TEAR_THRESHOLD = 0.55f

/** Koparirken kac tirtikta bir titresim verilecegi. */
private const val TEAR_TICKS = 9

private val CardShape = RoundedCornerShape(4.dp)

/**
 * Ucus oncesi bilet. Kart yazicidan kademeli olarak beslenir, sonra kullanici
 * perforasyon boyunca parmagiyla yirtar; ucus ancak bilet koparilinca baslar.
 */
@Composable
fun TicketScreen(
    ticket: Ticket,
    onDeparture: () -> Unit,
    onBack: () -> Unit,
    onPrint: () -> Unit,
    onTearTick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFlightColors.current
    val scope = rememberCoroutineScope()

    val feed = remember { Animatable(0f) }
    var tear by remember { mutableFloatStateOf(0f) }
    var torn by remember { mutableStateOf(false) }
    var lastTick by remember { mutableIntStateOf(0) }
    var cardHeight by remember { mutableIntStateOf(0) }
    val printed = feed.value > 0.995f

    LaunchedEffect(Unit) {
        onPrint()
        // Yazici kagidi tek hamlede degil, duraklayarak besler.
        feed.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = PRINT_MS
                0f at 0
                0.26f at 320
                0.28f at 460
                0.58f at 820
                0.61f at 980
                0.88f at 1400
                0.90f at 1540
                1f at PRINT_MS
            },
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .padding(horizontal = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton("← Geri", colors, onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Text(
                when {
                    torn -> "İYİ UÇUŞLAR"
                    printed -> "BİLETİNİZİ KOPARIN"
                    else -> "BİLET KESİLİYOR"
                },
                style = labelStyle(colors.ink).copy(fontSize = 15.sp, letterSpacing = 2.6.sp),
            )
        }

        // yazici yuvasi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(colors.panel2, CardShape)
                .border(1.dp, colors.line, CardShape),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            Column(
                modifier = Modifier
                    .onSizeChanged { cardHeight = it.height }
                    .offset { IntOffset(0, ((feed.value - 1f) * cardHeight).toInt()) },
            ) {
                TicketStub(ticket, colors)

                TearLine(
                    tear = tear,
                    colors = colors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp),
                )

                TicketBody(
                    ticket = ticket,
                    colors = colors,
                    modifier = Modifier
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(1f, 0f)
                            rotationZ = -4.5f * tear
                            translationY = (if (torn) 26f else 10f) * tear * density
                        }
                        .pointerInput(printed, torn) {
                            if (!printed || torn) return@pointerInput
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    scope.launch {
                                        if (tear >= TEAR_THRESHOLD) {
                                            animate(tear, 1f, animationSpec = tween(200)) { v, _ ->
                                                tear = v
                                            }
                                            torn = true
                                            onTearTick()
                                        } else {
                                            animate(tear, 0f, animationSpec = tween(260, easing = FastOutSlowInEasing)) { v, _ ->
                                                tear = v
                                            }
                                            lastTick = 0
                                        }
                                    }
                                },
                            ) { change, amount ->
                                change.consume()
                                val width = size.width.toFloat().coerceAtLeast(1f)
                                tear = (tear + amount / width).coerceIn(0f, 1f)
                                val step = (tear * TEAR_TICKS).toInt()
                                if (step != lastTick) {
                                    lastTick = step
                                    onTearTick()
                                }
                            }
                        },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            when {
                torn -> "Bilet elinde. İyi uçuşlar."
                printed -> "koparmak için parmağını bilet üzerinde sağa kaydır"
                else -> "bilet kesiliyor…"
            },
            style = monoStyle(colors.muted, 11),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(14.dp))
        FlightButton(
            text = "Uçuşa başla",
            colors = colors,
            primary = true,
            enabled = torn,
            modifier = Modifier.fillMaxWidth(),
            onClick = onDeparture,
        )
    }
}

/** Yuvada kalan koçan: havayolu ve rota. */
@Composable
private fun TicketStub(ticket: Ticket, colors: FlightColors) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel, CardShape)
            .border(1.dp, colors.line, CardShape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "UÇUŞ MODU",
                style = labelStyle(colors.amber).copy(fontSize = 13.sp, letterSpacing = 3.sp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "BİNİŞ KARTI",
                style = labelStyle(colors.muted).copy(fontSize = 10.sp, letterSpacing = 2.sp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    ticket.route.origin.code,
                    style = labelStyle(colors.ink).copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    ),
                )
                Text(ticket.route.origin.city, style = monoStyle(colors.muted, 11))
            }
            Spacer(Modifier.weight(1f))
            Text("✈", style = monoStyle(colors.amber, 18))
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    ticket.route.dest.code,
                    style = labelStyle(colors.ink).copy(
                        fontSize = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    ),
                )
                Text(ticket.route.dest.city, style = monoStyle(colors.muted, 11))
            }
        }
    }
}

/** Koparilan parca: yolcu bilgileri ve barkod. */
@Composable
private fun TicketBody(ticket: Ticket, colors: FlightColors, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.panel, CardShape)
            .border(1.dp, colors.line, CardShape)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            Field("Yolcu", ticket.passenger, colors, Modifier.weight(2f))
            Field("Uçuş", ticket.flightNo, colors, Modifier.weight(1.2f))
            Field("Kapı", ticket.gate, colors, Modifier.weight(1f))
            Field("Koltuk", ticket.seat, colors, Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Field("Biniş", formatTimeOfDay(ticket.boardingAt), colors, Modifier.weight(1f))
            Field("Kalkış", formatTimeOfDay(ticket.departureAt), colors, Modifier.weight(1f))
            Field(
                "Varış",
                formatTimeOfDay(ticket.arrivalAt, ticket.boardingAt),
                colors,
                Modifier.weight(1.4f),
            )
            Field("Sınıf", "ODAK", colors, Modifier.weight(0.9f))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Field("Mesafe", "${formatNumber(ticket.distanceKm)} km", colors, Modifier.weight(1.3f))
            Field("Bacak", "${ticket.legs} pomodoro", colors, Modifier.weight(1.3f))
            Field("Odak", formatDuration(ticket.focusMinutes), colors, Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Barcode(ticket, colors)
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    colors: FlightColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label.uppercase(), style = labelStyle(colors.muted).copy(fontSize = 9.sp))
        Spacer(Modifier.height(2.dp))
        Text(value, style = monoStyle(colors.ink, 13, FontWeight.Medium))
    }
}

/**
 * Koparma cizgisi. [tear] kadarlik kismi tirtikli bir yirtik olarak, kalani
 * delikli cizgi olarak cizilir; kenarlarda yarim daire centikler durur.
 */
@Composable
private fun TearLine(tear: Float, colors: FlightColors, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val y = size.height / 2f
        val notch = 5.dp.toPx()
        val tornX = tear * size.width

        // yirtilmis kisim: dogrusal degil, tirtikli
        if (tornX > 1f) {
            var x = 0f
            var up = true
            val jag = 3.dp.toPx()
            val stride = 6.dp.toPx()
            while (x < tornX) {
                val next = minOf(x + stride, tornX)
                drawLine(
                    color = colors.ink.copy(alpha = 0.35f),
                    start = Offset(x, if (up) y - jag else y + jag),
                    end = Offset(next, if (up) y + jag else y - jag),
                    strokeWidth = 1.2.dp.toPx(),
                )
                x = next
                up = !up
            }
        }

        // henuz koparilmamis kisim: delikli cizgi
        var x = tornX
        val dash = 5.dp.toPx()
        val gap = 4.dp.toPx()
        while (x < size.width) {
            drawLine(
                color = colors.line,
                start = Offset(x, y),
                end = Offset(minOf(x + dash, size.width), y),
                strokeWidth = 1.dp.toPx(),
            )
            x += dash + gap
        }

        // kenar centikleri
        drawCircle(colors.ground, radius = notch, center = Offset(0f, y))
        drawCircle(colors.ground, radius = notch, center = Offset(size.width, y))
    }
}

@Composable
private fun Barcode(ticket: Ticket, colors: FlightColors) {
    val pattern = remember(ticket.flightNo, ticket.seat) { ticket.barcode }
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
        ) {
            val unit = size.width / pattern.sumOf { it + 1 }.toFloat()
            var x = 0f
            for ((index, width) in pattern.withIndex()) {
                if (index % 2 == 0) {
                    drawRect(
                        color = colors.ink,
                        topLeft = Offset(x, 0f),
                        size = Size(width * unit, size.height),
                    )
                }
                x += (width + 1) * unit
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "${ticket.flightNo} · ${ticket.route.key} · ${ticket.seat}",
            style = monoStyle(colors.muted, 10).copy(letterSpacing = 2.sp),
        )
    }
}
