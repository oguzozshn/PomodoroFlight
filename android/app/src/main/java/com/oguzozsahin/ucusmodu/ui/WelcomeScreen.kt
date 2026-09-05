package com.oguzozsahin.ucusmodu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.R
import com.oguzozsahin.ucusmodu.data.upperTr
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.KM_PER_MINUTE
import com.oguzozsahin.ucusmodu.data.EARTH_CIRCUMFERENCE_KM
import com.oguzozsahin.ucusmodu.data.currentLapFraction
import com.oguzozsahin.ucusmodu.data.formatHours
import com.oguzozsahin.ucusmodu.data.formatNumber
import com.oguzozsahin.ucusmodu.data.formatTimeOfDay
import com.oguzozsahin.ucusmodu.data.worldLaps
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.LocalFlightColors
import com.oguzozsahin.ucusmodu.ui.theme.labelStyle
import com.oguzozsahin.ucusmodu.ui.theme.monoStyle
import kotlinx.coroutines.delay

/**
 * Ilk adim: terminal. Gunun ucusuna buradan cikilir; yarim kalmis bir rota varsa
 * dogrudan kaldigi yerden devam edilebilir.
 */
@Composable
fun WelcomeScreen(
    state: FlightUiState,
    onStartJourney: () -> Unit,
    onResume: () -> Unit,
    onLogbook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFlightColors.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        // Tablette yatayda kure solda durur, metin ve dugmeler sagda kaydirilir;
        // hicbir seyi kaydirmadan hem dunyayi hem kalkis dugmesini gorursunuz.
        if (maxWidth >= TwoPaneMinWidth) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(PaneGap),
            ) {
                TerminalGlobe(
                    state = state,
                    colors = colors,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .widthIn(max = ContentMaxWidth)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    TerminalHeader(colors)
                    TerminalContent(state, colors, onStartJourney, onResume, onLogbook)
                }
            }
        } else {
            ReadableColumn(
                background = colors.ground,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TerminalHeader(colors)
                TerminalGlobe(
                    state = state,
                    colors = colors,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.05f),
                )
                TerminalContent(state, colors, onStartJourney, onResume, onLogbook)
            }
        }
    }
}

/** Uygulama adi ve terminal saati. */
@Composable
private fun TerminalHeader(colors: FlightColors) {
    // Terminal saati: yirmi saniyede bir tazelenir.
    val clock by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(20_000L)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_plane),
            contentDescription = null,
            colorFilter = ColorFilter.tint(colors.amber),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "UÇUŞ MODU",
            style = labelStyle(colors.ink).copy(
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 3.sp,
            ),
        )
        Spacer(Modifier.weight(1f))
        Text(formatTimeOfDay(clock), style = monoStyle(colors.muted, 13))
    }
}

/** Tamamlanmis ucuslarin izleriyle birlikte kure. */
@Composable
private fun TerminalGlobe(
    state: FlightUiState,
    colors: FlightColors,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(colors.space, PanelShape)
            .border(1.dp, colors.line, PanelShape),
    ) {
        GlobeMap(
            route = state.route,
            fraction = state.fraction,
            colors = colors,
            trails = state.log.map { it.routeKey },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** Baslik, kalkis dugmeleri ve toplamlar. */
@Composable
private fun TerminalContent(
    state: FlightUiState,
    colors: FlightColors,
    onStartJourney: () -> Unit,
    onResume: () -> Unit,
    onLogbook: () -> Unit,
) {
    val resumable = state.started && !state.arrived

    Column {
        Text(
            "BUGÜN NEREYE UÇUYORUZ?",
            style = labelStyle(colors.ink).copy(
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.6.sp,
            ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Bir rota seç, odakta geçen her dakika seni ${formatNumber(KM_PER_MINUTE)} km " +
                "ileri taşısın. Odaklandığın sürece uçak rotada ilerler.",
            style = monoStyle(colors.muted, 12),
        )
    }

    FlightButton(
        text = "Yolculuğa başla",
        colors = colors,
        primary = true,
        modifier = Modifier.fillMaxWidth(),
        onClick = onStartJourney,
    )

    if (resumable) {
        FlightButton(
            text = "Uçuşa devam et · ${state.route.origin.code}→${state.route.dest.code} " +
                "${state.done}/${state.route.legs}",
            colors = colors,
            modifier = Modifier.fillMaxWidth(),
            onClick = onResume,
        )
    }

    // Panelin tamami mil bildirimine acilan kapi.
    Panel(colors, modifier = Modifier.clickable { onLogbook() }) {
        Column(Modifier.padding(14.dp)) {
            Row {
                Stat("Toplam odak", formatHours(state.totalMs), colors, Modifier.weight(1f))
                Stat("Kat edilen yol", "${formatNumber(state.totalKm)} km", colors, Modifier.weight(1f))
                Stat("Tamamlanan", "${state.log.size} uçuş", colors, Modifier.weight(1f))
            }
            Spacer(Modifier.height(16.dp))
            WorldTour(state.totalKm, colors)
            Spacer(Modifier.height(12.dp))
            Text(
                "MİL BİLDİRİMİNİ AÇ  →",
                style = labelStyle(colors.amber).copy(fontSize = 10.sp),
            )
        }
    }
}

/** Dunyanin cevresine gore ilerleme: 40.075 km bir tur. */
@Composable
private fun WorldTour(totalKm: Double, colors: FlightColors) {
    val laps = worldLaps(totalKm)
    val fraction = currentLapFraction(totalKm)
    val completed = laps.toInt()

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DÜNYA TURU", style = labelStyle(colors.muted).copy(fontSize = 10.sp))
            Spacer(Modifier.weight(1f))
            Text(
                if (completed > 0) {
                    "$completed tur · %${(fraction * 100).toInt()}"
                } else {
                    "%${(fraction * 100).toInt()}"
                },
                style = monoStyle(colors.amber, 12, FontWeight.Medium),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(colors.panel2, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction.coerceIn(0f, 1f))
                    .height(6.dp)
                    .background(colors.amber, RoundedCornerShape(3.dp)),
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            "${formatNumber(totalKm)} / ${formatNumber(EARTH_CIRCUMFERENCE_KM)} km",
            style = monoStyle(colors.muted, 10),
        )
    }
}

@Composable
private fun Stat(label: String, value: String, colors: FlightColors, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label.upperTr(), style = labelStyle(colors.muted).copy(fontSize = 10.sp))
        Spacer(Modifier.height(3.dp))
        Text(value, style = monoStyle(colors.ink, 15, FontWeight.Medium))
    }
}
