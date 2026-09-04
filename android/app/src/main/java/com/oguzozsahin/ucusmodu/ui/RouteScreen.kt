package com.oguzozsahin.ucusmodu.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.data.Airports
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.Presets
import com.oguzozsahin.ucusmodu.data.Route
import com.oguzozsahin.ucusmodu.data.focusMinutesFor
import com.oguzozsahin.ucusmodu.data.formatDuration
import com.oguzozsahin.ucusmodu.data.formatNumber
import com.oguzozsahin.ucusmodu.data.formatTimeOfDay
import com.oguzozsahin.ucusmodu.data.gateOf
import com.oguzozsahin.ucusmodu.data.realFlightMinutes
import com.oguzozsahin.ucusmodu.data.remainingScheduleMinutes
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.LocalFlightColors
import com.oguzozsahin.ucusmodu.ui.theme.labelStyle
import com.oguzozsahin.ucusmodu.ui.theme.monoStyle

/**
 * Ikinci adim: rotayi sec, ozetini gor, ucusa basla.
 * Secilen rota aninda kureye yansir; ozet paneli de onunla birlikte degisir.
 */
@Composable
fun RouteScreen(
    state: FlightUiState,
    actions: FlightActions,
    onBack: () -> Unit,
    onDeparture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFlightColors.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton("← Geri", colors, onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Text(
                "ROTA SEÇ",
                style = labelStyle(colors.ink).copy(fontSize = 15.sp, letterSpacing = 2.6.sp),
            )
        }

        // Secim degistikce donen kure: hangi rotayi kurdugun aninda gorunur.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.25f)
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

        RouteSummary(state, colors, onDeparture)
        PresetList(state, colors, actions)
        CustomRouteCard(state, colors, actions)
    }
}

/** Secilen rotanin binis karti: ucus no, kapi, saatler ve mesafe. */
@Composable
private fun RouteSummary(state: FlightUiState, colors: FlightColors, onDeparture: () -> Unit) {
    val route = state.route
    val focusMinutes = focusMinutesFor(route.legs - state.done, state.focusMin)
    val realMinutes = realFlightMinutes(route.km)
    val scheduleMinutes = remainingScheduleMinutes(
        legsLeft = state.legsLeft,
        focusMin = state.focusMin,
        breakMin = state.breakMin,
        cycle = state.cycle,
    )
    // Saatler her yeniden cizimde degil, rota degistikce tazelenir.
    val now = remember(route.key, scheduleMinutes) { System.currentTimeMillis() }
    val arrival = now + scheduleMinutes * 60_000L

    Panel(colors) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "BİNİŞ KARTI",
                style = labelStyle(colors.amber).copy(fontSize = 11.sp, letterSpacing = 3.sp),
            )
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = route.key,
                transitionSpec = { fadeIn(tween(260)).togetherWith(fadeOut(tween(180))) },
                label = "rota-ozeti",
            ) { _ ->
                Column {
                    Text(
                        "${route.origin.city.uppercase()} → ${route.dest.city.uppercase()}",
                        style = labelStyle(colors.ink).copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 1.4.sp,
                        ),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${route.origin.code} — ${route.dest.code} · ${route.flightNo}",
                        style = monoStyle(colors.muted, 12),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.lineSoft)
            Spacer(Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                BoardingCell("Uçuş", route.flightNo, colors, Modifier.weight(1f))
                BoardingCell("Kapı", gateOf(route.key), colors, Modifier.weight(1f))
                BoardingCell("Bacak", "${state.done}/${route.legs}", colors, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                BoardingCell("Kalkış", formatTimeOfDay(now), colors, Modifier.weight(1f))
                BoardingCell("Varış", formatTimeOfDay(arrival, now), colors, Modifier.weight(1f))
                BoardingCell("Süre", formatDuration(scheduleMinutes), colors, Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = colors.lineSoft)
            Spacer(Modifier.height(12.dp))

            SummaryRow("Mesafe", "${formatNumber(route.km)} km", colors)
            SummaryRow("Odakta geçecek", formatDuration(focusMinutes), colors)
            SummaryRow("Gerçek uçuş süresi", "≈ ${formatDuration(realMinutes)}", colors)

            Spacer(Modifier.height(18.dp))
            FlightButton(
                text = if (state.arrived) "Bu rota tamamlandı" else "Uçuşa başlat",
                colors = colors,
                primary = true,
                enabled = !state.arrived,
                modifier = Modifier.fillMaxWidth(),
                onClick = onDeparture,
            )
        }
    }
}

@Composable
private fun BoardingCell(
    label: String,
    value: String,
    colors: FlightColors,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(label.uppercase(), style = labelStyle(colors.muted).copy(fontSize = 10.sp))
        Spacer(Modifier.height(3.dp))
        Text(value, style = monoStyle(colors.ink, 15, FontWeight.Medium))
    }
}

@Composable
private fun SummaryRow(label: String, value: String, colors: FlightColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label.uppercase(), style = labelStyle(colors.muted).copy(fontSize = 11.sp))
        Spacer(Modifier.weight(1f))
        Text(value, style = monoStyle(colors.ink, 13, FontWeight.Medium))
    }
}

@Composable
private fun PresetList(state: FlightUiState, colors: FlightColors, actions: FlightActions) {
    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            PanelHead("Hazır rotalar", colors)
            Spacer(Modifier.height(10.dp))

            for (preset in Presets.list) {
                val presetRoute = remember(preset.key) { Route.of(preset.key) } ?: continue
                val progress = (state.progress[preset.key] ?: 0).coerceAtMost(presetRoute.legs)
                val active = preset.key == state.route.key

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .background(
                            if (active) colors.amber.copy(alpha = 0.10f) else colors.panel2,
                            PanelShape,
                        )
                        .border(1.dp, if (active) colors.amber else colors.line, PanelShape)
                        .clickable { actions.onRoute(preset.key) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${preset.from} → ${preset.to}",
                            style = monoStyle(
                                if (active) colors.amber else colors.ink,
                                13,
                                FontWeight.Medium,
                            ),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(preset.nick, style = labelStyle(colors.ink2).copy(fontSize = 12.sp))
                    }
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "${formatNumber(presetRoute.km)} km · ${presetRoute.legs} pom" +
                            if (progress > 0) " · $progress✓" else "",
                        style = monoStyle(colors.muted, 11),
                    )
                }
            }
        }
    }
}

@Composable
private fun CustomRouteCard(state: FlightUiState, colors: FlightColors, actions: FlightActions) {
    var from by remember(state.route.key) { mutableStateOf(state.route.origin.code) }
    var to by remember(state.route.key) { mutableStateOf(state.route.dest.code) }
    var message by remember { mutableStateOf("") }

    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            PanelHead("Kendi rotanı kur", colors)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                AirportDropdown(from, colors, Modifier.weight(1f)) { from = it }
                Text(
                    "→",
                    style = monoStyle(colors.muted, 14),
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                AirportDropdown(to, colors, Modifier.weight(1f)) { to = it }
            }
            Spacer(Modifier.height(10.dp))
            FlightButton(
                text = "Rotayı kur",
                colors = colors,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (from == to) {
                    message = "Kalkış ve varış aynı olamaz — başka bir varış seç."
                } else {
                    val route = Route.of(from, to)
                    if (route == null) {
                        message = "Bu rota kurulamadı."
                    } else {
                        actions.onRoute(route.key)
                        message = ""
                    }
                }
            }
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(message, style = monoStyle(colors.ink2, 11))
            }
        }
    }
}

@Composable
private fun AirportDropdown(
    code: String,
    colors: FlightColors,
    modifier: Modifier = Modifier,
    onSelect: (String) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val airport = Airports[code]

    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.panel2, PanelShape)
                .border(1.dp, colors.line, PanelShape)
                .clickable { open = true }
                .padding(horizontal = 10.dp, vertical = 9.dp),
        ) {
            Text(airport?.city ?: code, style = monoStyle(colors.ink, 12))
            Text(code, style = monoStyle(colors.muted, 10))
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier
                .background(colors.panel)
                .height(360.dp),
        ) {
            for (item in Airports.codesByCity) {
                val ap = Airports[item] ?: continue
                DropdownMenuItem(
                    text = {
                        Text(
                            "${ap.city} (${ap.code})",
                            style = monoStyle(
                                if (item == code) colors.amber else colors.ink2,
                                12,
                            ),
                        )
                    },
                    onClick = {
                        onSelect(item)
                        open = false
                    },
                )
            }
        }
    }
}
