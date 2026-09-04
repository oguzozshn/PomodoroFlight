package com.oguzozsahin.ucusmodu.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.formatHours
import com.oguzozsahin.ucusmodu.data.formatNumber
import com.oguzozsahin.ucusmodu.data.formatTimeOfDay
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
    modifier: Modifier = Modifier,
) {
    val colors = LocalFlightColors.current
    val resumable = state.done > 0 && !state.arrived

    // Terminal saati: dakikada bir tazelenir.
    val clock by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(20_000L)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp),
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

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.05f)
                .background(colors.space, PanelShape)
                .border(1.dp, colors.line, PanelShape),
        ) {
            GlobeMap(
                route = state.route,
                fraction = state.fraction,
                colors = colors,
                modifier = Modifier.fillMaxSize(),
            )
        }

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
                "Bir rota seç, her pomodoro seni 850 km ileri taşısın. " +
                    "Odaklandığın sürece uçak rotada ilerler.",
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

        Panel(colors) {
            Row(Modifier.padding(14.dp)) {
                Stat("Toplam odak", formatHours(state.totalMs), colors, Modifier.weight(1f))
                Stat("Kat edilen yol", "${formatNumber(state.totalKm)} km", colors, Modifier.weight(1f))
                Stat("Tamamlanan", "${state.log.size} uçuş", colors, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, colors: FlightColors, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label.uppercase(), style = labelStyle(colors.muted).copy(fontSize = 10.sp))
        Spacer(Modifier.height(3.dp))
        Text(value, style = monoStyle(colors.ink, 15, FontWeight.Medium))
    }
}
