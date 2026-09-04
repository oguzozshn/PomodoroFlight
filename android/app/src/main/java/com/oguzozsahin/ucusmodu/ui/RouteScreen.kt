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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.data.Airport
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.PlaceRegistry
import com.oguzozsahin.ucusmodu.data.Preset
import com.oguzozsahin.ucusmodu.data.Presets
import com.oguzozsahin.ucusmodu.data.Route
import com.oguzozsahin.ucusmodu.data.countryName
import com.oguzozsahin.ucusmodu.data.focusMinutesFor
import com.oguzozsahin.ucusmodu.data.formatDuration
import com.oguzozsahin.ucusmodu.data.formatNumber
import com.oguzozsahin.ucusmodu.data.formatTimeOfDay
import com.oguzozsahin.ucusmodu.data.gateOf
import com.oguzozsahin.ucusmodu.data.realFlightMinutes
import com.oguzozsahin.ucusmodu.data.remainingScheduleMinutes
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.LocalFlightColors
import com.oguzozsahin.ucusmodu.ui.theme.PlexMono
import com.oguzozsahin.ucusmodu.ui.theme.labelStyle
import com.oguzozsahin.ucusmodu.ui.theme.monoStyle
import kotlinx.coroutines.launch

/** Kalkis mi varis mi seciliyor. */
private enum class Slot { FROM, TO }

/**
 * Ikinci adim: kureden ya da aramadan kalkis ve varis sec, binis kartini gor,
 * ucusa basla.
 */
@Composable
fun RouteScreen(
    state: FlightUiState,
    actions: FlightActions,
    places: List<Airport>,
    onBack: () -> Unit,
    onDeparture: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFlightColors.current
    val globe = rememberGlobeState(state.route.center)
    val scope = rememberCoroutineScope()

    var from by remember { mutableStateOf(state.route.origin.code) }
    var to by remember { mutableStateOf(state.route.dest.code) }
    var active by remember { mutableStateOf(Slot.FROM) }
    var query by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    val results = remember(query, places) {
        if (query.isBlank()) emptyList() else PlaceRegistry.search(query, 8)
    }

    fun flyToPlace(place: Airport) {
        scope.launch {
            globe.flyTo(place.lat, place.lon, targetZoom = maxOf(globe.zoom, 2.6f))
        }
    }

    fun choose(place: Airport) {
        message = ""
        query = ""
        if (active == Slot.FROM) {
            from = place.code
            active = Slot.TO
            if (from == to) {
                message = "Kalkış ve varış aynı olamaz — bir varış seç."
                flyToPlace(place)
                return
            }
        } else {
            if (place.code == from) {
                message = "Kalkış ve varış aynı olamaz — başka bir varış seç."
                return
            }
            to = place.code
            active = Slot.FROM
        }
        val route = Route.of(from, to)
        if (route == null) {
            message = "Bu rota kurulamadı."
            return
        }
        actions.onRoute(route.key)
        if (active == Slot.FROM) {
            // Rota tamamlandi: tumunu gorecek sekilde geri cekil.
            scope.launch {
                globe.flyTo(route.center.lat, route.center.lon, targetZoom = 1f, resetTouch = true)
            }
        } else {
            flyToPlace(place)
        }
    }

    // Kure kaydirilabilir alanin disinda duruyor: aksi halde surukleme jestini
    // sayfa kaydirma kapiyor ve kure donmuyor.
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .padding(top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GhostButton("← Geri", colors, onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Text(
                "ROTA SEÇ",
                style = labelStyle(colors.ink).copy(fontSize = 15.sp, letterSpacing = 2.6.sp),
            )
        }

        // Kure: surukle, cimdikle, sehre dokun.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(300.dp)
                .background(colors.space, PanelShape)
                .border(1.dp, colors.line, PanelShape),
        ) {
            GlobeMap(
                route = state.route,
                fraction = state.fraction,
                colors = colors,
                modifier = Modifier.fillMaxSize(),
                interactive = true,
                state = globe,
                places = places,
                highlighted = setOf(from, to),
                onPlaceTap = { choose(it) },
            )

            if (!globe.untouched) {
                GhostButton(
                    text = "Rotaya dön",
                    colors = colors,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp),
                ) {
                    scope.launch {
                        globe.flyTo(
                            state.route.center.lat,
                            state.route.center.lon,
                            targetZoom = 1f,
                            resetTouch = true,
                        )
                    }
                }
            } else {
                Text(
                    "sürükle · çift dokun · şehre dokun · +/− yakınlaştırır",
                    style = monoStyle(colors.muted, 10),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 10.dp),
                )
            }

            // Cimdiklemenin zor oldugu yerler icin (emulator, fare) acik dugmeler.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    "%.1f×".format(globe.zoom),
                    style = monoStyle(colors.ink2, 11)
                        .copy(background = colors.panel.copy(alpha = 0.85f)),
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                ZoomButton("+", colors, enabled = globe.canZoomIn) {
                    scope.launch { globe.animateZoomTo(globe.zoom * 1.7f) }
                }
                // Fareyle de surukelenebilen kol: cimdiklemenin calismadigi
                // ortamlarda surekli yakinlastirmanin garanti yolu.
                ZoomSlider(
                    zoom = globe.zoom,
                    minZoom = GLOBE_MIN_ZOOM,
                    maxZoom = GLOBE_MAX_ZOOM,
                    colors = colors,
                ) { globe.snapZoomTo(it) }
                ZoomButton("−", colors, enabled = globe.canZoomOut) {
                    scope.launch { globe.animateZoomTo(globe.zoom / 1.7f) }
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp)
                .padding(top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SlotRow(
                from = PlaceRegistry.find(from),
                to = PlaceRegistry.find(to),
                active = active,
                colors = colors,
                onSelect = { active = it },
            )

            SearchCard(
                query = query,
                results = results,
                active = active,
                colors = colors,
                onQuery = { query = it },
                onPick = { choose(it) },
            )

            if (message.isNotEmpty()) {
                Text(message, style = monoStyle(colors.amber, 11))
            }

            RouteSummary(state, colors, onDeparture)

            PresetList(state, colors) { preset ->
                from = preset.from
                to = preset.to
                active = Slot.FROM
                message = ""
                actions.onRoute(preset.key)
                Route.of(preset.key)?.let { route ->
                    scope.launch {
                        globe.flyTo(route.center.lat, route.center.lon, targetZoom = 1f, resetTouch = true)
                    }
                }
            }
        }
    }
}

/** Kalkis ve varis yuvalari; dokununca hangisinin secildigi degisir. */
@Composable
private fun SlotRow(
    from: Airport?,
    to: Airport?,
    active: Slot,
    colors: FlightColors,
    onSelect: (Slot) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        SlotCard("Kalkış", from, active == Slot.FROM, colors, Modifier.weight(1f)) {
            onSelect(Slot.FROM)
        }
        Text(
            "→",
            style = monoStyle(colors.muted, 16),
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        SlotCard("Varış", to, active == Slot.TO, colors, Modifier.weight(1f)) {
            onSelect(Slot.TO)
        }
    }
}

@Composable
private fun SlotCard(
    label: String,
    place: Airport?,
    selected: Boolean,
    colors: FlightColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .background(
                if (selected) colors.amber.copy(alpha = 0.10f) else colors.panel,
                PanelShape,
            )
            .border(1.dp, if (selected) colors.amber else colors.line, PanelShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            label.uppercase(),
            style = labelStyle(if (selected) colors.amber else colors.muted).copy(fontSize = 10.sp),
        )
        Spacer(Modifier.height(3.dp))
        Text(
            place?.city ?: "—",
            style = monoStyle(colors.ink, 15, FontWeight.Medium),
        )
        Text(
            if (place != null) "${place.code} · ${countryName(place.country)}" else "seç",
            style = monoStyle(colors.muted, 10),
        )
    }
}

@Composable
private fun SearchCard(
    query: String,
    results: List<Airport>,
    active: Slot,
    colors: FlightColors,
    onQuery: (String) -> Unit,
    onPick: (Airport) -> Unit,
) {
    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            Text(
                if (active == Slot.FROM) "KALKIŞ ŞEHRİ ARA" else "VARIŞ ŞEHRİ ARA",
                style = labelStyle(colors.muted).copy(fontSize = 11.sp),
            )
            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.panel2, PanelShape)
                    .border(1.dp, colors.line, PanelShape)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
            ) {
                if (query.isEmpty()) {
                    Text(
                        "şehir ya da kod — örn. Tokyo, ADB",
                        style = monoStyle(colors.muted, 12),
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = monoStyle(colors.ink, 12).copy(fontFamily = PlexMono),
                    cursorBrush = SolidColor(colors.amber),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (results.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                for (place in results) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(place) }
                            .padding(vertical = 8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                place.city,
                                style = monoStyle(colors.ink, 13, FontWeight.Medium),
                            )
                            Spacer(Modifier.weight(1f))
                            Text(place.code, style = monoStyle(colors.amber, 12, FontWeight.Medium))
                        }
                        Text(
                            listOfNotNull(
                                place.airportName.takeIf { it.isNotBlank() },
                                countryName(place.country),
                            ).joinToString(" · "),
                            style = monoStyle(colors.muted, 10),
                        )
                    }
                    HorizontalDivider(color = colors.lineSoft)
                }
            } else if (query.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text("Sonuç yok.", style = monoStyle(colors.muted, 11))
            }
        }
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
                        "${countryName(route.origin.country)} → ${countryName(route.dest.country)}",
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
private fun PresetList(
    state: FlightUiState,
    colors: FlightColors,
    onPick: (Preset) -> Unit,
) {
    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            PanelHead("Hazır rotalar", colors)
            Spacer(Modifier.height(10.dp))

            for (preset in Presets.list) {
                val presetRoute = remember(preset.key) { Route.of(preset.key) } ?: continue
                val progress = (state.progress[preset.key] ?: 0).coerceAtMost(presetRoute.legs)
                val selected = preset.key == state.route.key

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .background(
                            if (selected) colors.amber.copy(alpha = 0.10f) else colors.panel2,
                            PanelShape,
                        )
                        .border(1.dp, if (selected) colors.amber else colors.line, PanelShape)
                        .clickable { onPick(preset) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${preset.from} → ${preset.to}",
                            style = monoStyle(
                                if (selected) colors.amber else colors.ink,
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
