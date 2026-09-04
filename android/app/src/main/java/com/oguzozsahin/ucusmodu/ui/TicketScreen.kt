package com.oguzozsahin.ucusmodu.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
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

private const val PRINT_MS = 1500

/**
 * Ucus oncesi bilet kesme. Kart yazici yuvasindan asagi dogru basilarak cikar;
 * ekrana dokunmak animasyonu atlar.
 */
@Composable
fun TicketScreen(
    ticket: Ticket,
    onDeparture: () -> Unit,
    onBack: () -> Unit,
    onPrint: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFlightColors.current
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    var cardHeight by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        onPrint()
        progress.animateTo(1f, tween(PRINT_MS, easing = FastOutSlowInEasing))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                scope.launch { progress.snapTo(1f) }
            }
            .padding(horizontal = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton("← Geri", colors, onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Text(
                if (progress.value < 1f) "BİLET KESİLİYOR" else "BİLETİNİZ HAZIR",
                style = labelStyle(colors.ink).copy(fontSize = 15.sp, letterSpacing = 2.6.sp),
            )
        }

        // yazici yuvasi
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(colors.panel2, PanelShape)
                .border(1.dp, colors.line, PanelShape),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
        ) {
            TicketCard(
                ticket = ticket,
                colors = colors,
                modifier = Modifier
                    .onSizeChanged { cardHeight = it.height }
                    .offset { IntOffset(0, ((progress.value - 1f) * cardHeight).toInt()) },
            )
        }

        Spacer(Modifier.height(18.dp))
        FlightButton(
            text = "Uçuşa başla",
            colors = colors,
            primary = true,
            enabled = progress.value > 0.85f,
            modifier = Modifier.fillMaxWidth(),
            onClick = onDeparture,
        )
    }
}

@Composable
private fun TicketCard(ticket: Ticket, colors: FlightColors, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.panel, PanelShape)
            .border(1.dp, colors.line, PanelShape)
            .padding(16.dp),
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

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(
                    ticket.route.origin.code,
                    style = labelStyle(colors.ink).copy(
                        fontSize = 34.sp,
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
                        fontSize = 34.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    ),
                )
                Text(ticket.route.dest.city, style = monoStyle(colors.muted, 11))
            }
        }

        Spacer(Modifier.height(18.dp))
        Perforation(colors)
        Spacer(Modifier.height(16.dp))

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
            Field("Varış", formatTimeOfDay(ticket.arrivalAt, ticket.boardingAt), colors, Modifier.weight(1.4f))
            Field("Sınıf", "ODAK", colors, Modifier.weight(0.9f))
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Field("Mesafe", "${formatNumber(ticket.distanceKm)} km", colors, Modifier.weight(1.3f))
            Field("Bacak", "${ticket.legs} pomodoro", colors, Modifier.weight(1.3f))
            Field("Odak", formatDuration(ticket.focusMinutes), colors, Modifier.weight(1f))
        }

        Spacer(Modifier.height(18.dp))
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

/** Koparma cizgisi: kenarlarda yarim daire centikler, arada kesikli cizgi. */
@Composable
private fun Perforation(colors: FlightColors) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(12.dp),
    ) {
        val y = size.height / 2f
        val notch = 6.dp.toPx()
        drawCircle(colors.ground, radius = notch, center = Offset(-notch / 2, y))
        drawCircle(
            colors.ground,
            radius = notch,
            center = Offset(size.width + notch / 2, y),
        )
        var x = notch
        val dash = 5.dp.toPx()
        val gap = 4.dp.toPx()
        while (x < size.width - notch) {
            drawLine(
                color = colors.line,
                start = Offset(x, y),
                end = Offset(minOf(x + dash, size.width - notch), y),
                strokeWidth = 1.dp.toPx(),
            )
            x += dash + gap
        }
    }
}

@Composable
private fun Barcode(ticket: Ticket, colors: FlightColors) {
    val pattern = remember(ticket.flightNo, ticket.seat) { ticket.barcode }
    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp),
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
