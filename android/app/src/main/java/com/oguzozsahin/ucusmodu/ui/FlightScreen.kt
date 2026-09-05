package com.oguzozsahin.ucusmodu.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.data.upperTr
import com.oguzozsahin.ucusmodu.data.Airports
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.KM_PER_MINUTE
import com.oguzozsahin.ucusmodu.data.Phase
import com.oguzozsahin.ucusmodu.data.ThemeChoice
import com.oguzozsahin.ucusmodu.data.formatClock
import com.oguzozsahin.ucusmodu.data.formatDate
import com.oguzozsahin.ucusmodu.data.formatHours
import com.oguzozsahin.ucusmodu.data.formatNumber
import com.oguzozsahin.ucusmodu.ui.theme.BarlowCondensed
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.LocalFlightColors
import com.oguzozsahin.ucusmodu.ui.theme.labelStyle
import com.oguzozsahin.ucusmodu.ui.theme.monoStyle
import kotlinx.coroutines.delay

/** Ekranin disariya actigi eylemler. */
data class FlightActions(
    val onToggle: () -> Unit,
    val onSkip: () -> Unit,
    val onReset: () -> Unit,
    val onRoute: (String) -> Unit,
    val onFocusMin: (Int) -> Unit,
    val onBreakMin: (Int) -> Unit,
    val onAutoBreak: (Boolean) -> Unit,
    val onSound: (Boolean) -> Unit,
    /** Gunlugu, ilerlemeyi ve toplamlari birden siler. */
    val onResetEverything: () -> Unit,
    /** Sayaci dogrudan baslatir (bilet ekranindaki "Uçuşa başla" icin). */
    val onStart: () -> Unit,
    val onPassenger: (String) -> Unit,
    /** Bilet basilirken kisa titresim. */
    val onTicketPrinted: () -> Unit,
    /** Bilet koparilirken her tirtikta tik. */
    val onTearTick: () -> Unit,
)

@Composable
fun FlightScreen(
    state: FlightUiState,
    actions: FlightActions,
    onChangeRoute: () -> Unit,
    onWindow: () -> Unit,
    onLogbook: () -> Unit,
    onBack: () -> Unit,
    theme: ThemeChoice,
    onTheme: (ThemeChoice) -> Unit,
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
        TopBar(state, colors, actions, onChangeRoute, onBack)
        MapPanel(state, colors, onWindow)
        TimerPanel(state, colors, actions)
        FlightPlanPanel(state, colors)
        SettingsPanel(state, colors, actions, theme, onTheme)
        LogPanel(state, colors, onLogbook)
        DangerPanel(colors, actions)

        Text(
            text = "Odakta geçen her dakika ${formatNumber(KM_PER_MINUTE)} km seyir. " +
                "İlerleme bu cihazda saklanır.",
            style = monoStyle(colors.muted, 11),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TopBar(
    state: FlightUiState,
    colors: FlightColors,
    actions: FlightActions,
    onChangeRoute: () -> Unit,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Cikis her ekranda ayni yerde: sol ust. Burada eskiden yalnizca
        // dekoratif bir ucak simgesi vardi ve terminale donus yolu yoktu.
        GhostButton("← Geri", colors, onClick = onBack)
        Spacer(Modifier.width(8.dp))
        Text(
            state.route.flightNo,
            style = monoStyle(colors.ink, 14, FontWeight.Medium),
        )
        Spacer(Modifier.weight(1f))
        GhostButton(text = "Rota", colors = colors, onClick = onChangeRoute)
        Spacer(Modifier.width(6.dp))
        GhostButton(
            text = if (state.sound) "Kabin sesi" else "Sessiz",
            colors = colors,
        ) { actions.onSound(!state.sound) }
    }
}

@Composable
private fun MapPanel(state: FlightUiState, colors: FlightColors, onWindow: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // Kompakt telefonlarda geri sayimin katlamanin altinda kalmamasi
            // icin kure biraz alcak tutuluyor.
            .aspectRatio(1.18f)
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

        HudCorner(
            code = state.route.origin.code,
            city = state.route.origin.city,
            colors = colors,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        )
        HudCorner(
            code = state.route.dest.code,
            city = state.route.dest.city,
            colors = colors,
            alignEnd = true,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
        )

        Column(modifier = Modifier.align(Alignment.BottomCenter)) {
            // Tam ekran pencere gorunumu: koltugun yanindaki cami acar.
            // Gostergelerin ustunde duruyor ki okunani ortmesin.
            GhostButton(
                text = "Pencereden bak",
                colors = colors,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(end = 12.dp, bottom = 10.dp),
                onClick = onWindow,
            )
            StatusPill(state, colors, Modifier.padding(start = 12.dp, bottom = 10.dp))
            Readout(state, colors)
        }
    }
}

@Composable
private fun HudCorner(
    code: String,
    city: String,
    colors: FlightColors,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(
            code,
            style = labelStyle(colors.ink).copy(
                fontFamily = BarlowCondensed,
                fontWeight = FontWeight.SemiBold,
                fontSize = 26.sp,
                letterSpacing = 1.6.sp,
            ),
        )
        Text(city, style = monoStyle(colors.muted, 11))
    }
}

@Composable
private fun StatusPill(state: FlightUiState, colors: FlightColors, modifier: Modifier = Modifier) {
    val dotColor = when {
        state.running && state.phase == Phase.FOCUS -> colors.amber
        state.running -> colors.teal
        else -> colors.muted
    }
    val text = when {
        state.arrived -> "İniş yapıldı · ${state.route.dest.city}"
        state.running && state.phase == Phase.FOCUS ->
            "Seyir halinde · ${formatNumber(state.route.kmPerLeg)} km/pom"
        state.running -> "Mola · kabin ışıkları kısık"
        state.started -> "Beklemede · rotada tutuluyor"
        else -> "Kalkışa hazır"
    }

    val animatedDot by animateColorAsState(dotColor, tween(400), label = "durum-noktasi")

    Row(
        modifier = modifier
            .background(colors.panel.copy(alpha = 0.86f), PanelShape)
            .border(1.dp, colors.line, PanelShape)
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(animatedDot, CircleShape),
        )
        Spacer(Modifier.width(7.dp))
        AnimatedContent(
            targetState = text,
            transitionSpec = { fadeIn(tween(220)).togetherWith(fadeOut(tween(180))) },
            label = "durum-metni",
        ) { value ->
            Text(value, style = monoStyle(colors.ink2, 11))
        }
    }
}

@Composable
private fun Readout(state: FlightUiState, colors: FlightColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.panel.copy(alpha = 0.94f))
            .border(1.dp, colors.lineSoft),
    ) {
        ReadoutCell("Kat edilen", state.flownKm.toFloat(), "km", colors, Modifier.weight(1f))
        ReadoutCell("Kalan", (state.route.km - state.flownKm).toFloat(), "km", colors, Modifier.weight(1f))
        ReadoutCell("İrtifa", state.altitudeM.toFloat(), "m", colors, Modifier.weight(1f))
        ReadoutCell("Varışa", state.legsLeft.toFloat(), "pom", colors, Modifier.weight(1f))
    }
}

@Composable
private fun ReadoutCell(
    label: String,
    value: Float,
    unit: String,
    colors: FlightColors,
    modifier: Modifier = Modifier,
) {
    // Deger sicramaz, sayarak yeni degere gider.
    val shown by animateFloatAsState(
        targetValue = value,
        animationSpec = tween(durationMillis = 340, easing = FastOutSlowInEasing),
        label = "gosterge-$label",
    )
    Column(modifier = modifier.padding(vertical = 8.dp, horizontal = 6.dp)) {
        Text(label.upperTr(), style = labelStyle(colors.muted).copy(fontSize = 10.sp))
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatNumber(shown.toDouble()), style = monoStyle(colors.ink, 14))
            Text(unit, style = monoStyle(colors.muted, 10), modifier = Modifier.padding(start = 2.dp))
        }
    }
}

@Composable
private fun TimerPanel(state: FlightUiState, colors: FlightColors, actions: FlightActions) {
    val phaseText = when (state.phase) {
        Phase.FOCUS -> "Odak · Seyir"
        Phase.BREAK -> "Mola · Kabin"
        Phase.LONG -> "Uzun mola · Aktarma"
    }
    val subline = when {
        state.arrived && state.phase == Phase.FOCUS ->
            "${state.route.dest.city}'a indin. Günlüğe damga düştü — yeni bir rota seç."
        state.phase == Phase.FOCUS ->
            "Bu pomodoro ${formatNumber(state.route.kmPerLeg)} km taşıyor."
        else -> "Uçak rotada bekliyor; mola bitince seyre devam."
    }
    val primaryLabel = when {
        state.running -> "Beklet"
        state.phase == Phase.FOCUS && state.started -> "Devam"
        state.phase == Phase.FOCUS -> "Kalkış"
        else -> "Molayı başlat"
    }

    // Odakta kehribar, molada teal: faz degisimi renkten de okunur.
    val accent by animateColorAsState(
        targetValue = if (state.phase == Phase.FOCUS) colors.amber else colors.teal,
        animationSpec = tween(500),
        label = "faz-rengi",
    )

    Panel(colors) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AnimatedContent(
                targetState = phaseText,
                transitionSpec = { fadeIn(tween(260)).togetherWith(fadeOut(tween(200))) },
                label = "faz-basligi",
            ) { value ->
                Text(
                    value.upperTr(),
                    style = labelStyle(accent).copy(fontSize = 12.sp, letterSpacing = 2.6.sp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                formatClock(state.remainingMs),
                style = monoStyle(colors.ink, 68, FontWeight.Light),
            )
            Spacer(Modifier.height(8.dp))
            AnimatedContent(
                targetState = subline,
                transitionSpec = { fadeIn(tween(260)).togetherWith(fadeOut(tween(200))) },
                label = "alt-satir",
            ) { value ->
                Text(
                    value,
                    style = monoStyle(colors.muted, 12),
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(18.dp))
            // Birincil dugme kendi satirinda: seans ortasinda "Beklet"e
            // basarken yanindaki sifirlamaya kaymak fazla kolaydi.
            FlightButton(
                text = primaryLabel,
                colors = colors,
                primary = true,
                enabled = !(state.phase == Phase.FOCUS && state.arrived),
                modifier = Modifier.fillMaxWidth(),
                onClick = actions.onToggle,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Varilmis rotada atlanacak bir sey yok.
                FlightButton(
                    text = "Atla",
                    colors = colors,
                    enabled = !(state.phase == Phase.FOCUS && state.arrived),
                    modifier = Modifier.weight(1f),
                    onClick = actions.onSkip,
                )
                // "Sifirla" adi "Her seyi sil" ile karisiyordu; bu yalnizca
                // suren fazi basa aliyor.
                FlightButton(
                    text = "Fazı başa al",
                    colors = colors,
                    modifier = Modifier.weight(1f),
                    onClick = actions.onReset,
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(width = 18.dp, height = 3.dp)
                            .background(
                                if (index < state.cycle) colors.amber else colors.line,
                                RoundedCornerShape(1.dp),
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun FlightPlanPanel(state: FlightUiState, colors: FlightColors) {
    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            PanelHead("Uçuş planı", colors) {
                Text("${state.done} / ${state.route.legs}", style = monoStyle(colors.muted, 12))
            }
            Spacer(Modifier.height(12.dp))
            LegTicks(state, colors)
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (state.arrived) {
                    "Rota tamamlandı · ${formatNumber(state.route.km)} km"
                } else {
                    "${state.legsLeft} pomodoro sonra ${state.route.dest.city}'a iniyorsun · " +
                        "toplam ${formatNumber(state.route.km)} km"
                },
                style = monoStyle(colors.muted, 11),
            )
        }
    }
}

@Composable
private fun LegTicks(state: FlightUiState, colors: FlightColors) {
    // Bacaklar satira sigmazsa alt satira dokulur.
    val perRow = 12
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        for (rowStart in 0 until state.route.legs step perRow) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                for (i in rowStart until minOf(rowStart + perRow, state.route.legs)) {
                    val tick = when {
                        i < state.done -> TickState.DONE
                        i == state.done && state.running && state.phase == Phase.FOCUS -> TickState.NOW
                        else -> TickState.TODO
                    }
                    Tick(tick, colors)
                }
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    state: FlightUiState,
    colors: FlightColors,
    actions: FlightActions,
    theme: ThemeChoice,
    onTheme: (ThemeChoice) -> Unit,
) {
    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            PanelHead("Ayarlar", colors)
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("ODAK", style = labelStyle(colors.ink2).copy(fontSize = 12.sp))
                Spacer(Modifier.weight(1f))
                // Seyir sirasinda kilitli: olcek dakikaya bagli oldugu icin
                // suren pomodoronun ortasinda uzunlugu degistirmek, 25 dakika
                // odaklanip 50 dakikalik mesafe kazanmak demek olurdu.
                SegmentedOptions(
                    options = listOf(15, 25, 50),
                    selected = state.focusMin,
                    colors = colors,
                    enabled = !state.running,
                    onSelect = actions.onFocusMin,
                )
            }
            if (state.running) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Odak süresi seyir sırasında değiştirilemez — önce beklet.",
                    style = monoStyle(colors.muted, 10),
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("MOLA", style = labelStyle(colors.ink2).copy(fontSize = 12.sp))
                Spacer(Modifier.weight(1f))
                SegmentedOptions(listOf(5, 10), state.breakMin, colors, onSelect = actions.onBreakMin)
            }
            Spacer(Modifier.height(14.dp))
            Text("GÖRÜNÜM", style = labelStyle(colors.ink2).copy(fontSize = 12.sp))
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for (option in ThemeChoice.entries) {
                    val active = option == theme
                    Text(
                        text = option.title.upperTr(),
                        style = labelStyle(if (active) colors.amber else colors.muted).copy(
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (active) colors.amber.copy(alpha = 0.12f) else colors.panel2,
                                PanelShape,
                            )
                            .border(1.dp, if (active) colors.amber else colors.line, PanelShape)
                            .clickable { onTheme(option) }
                            .padding(vertical = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("YOLCU ADI", style = labelStyle(colors.muted).copy(fontSize = 10.sp))
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.panel2, PanelShape)
                    .border(1.dp, colors.line, PanelShape)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
            ) {
                if (state.passenger.isEmpty()) {
                    Text("biletinizde görünecek ad", style = monoStyle(colors.muted, 12))
                }
                BasicTextField(
                    value = state.passenger,
                    onValueChange = actions.onPassenger,
                    singleLine = true,
                    textStyle = monoStyle(colors.ink, 12),
                    cursorBrush = SolidColor(colors.amber),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Molalar kendiliğinden başlasın",
                    style = monoStyle(colors.ink2, 12),
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = state.autoBreak,
                    onCheckedChange = actions.onAutoBreak,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.panel,
                        checkedTrackColor = colors.amber,
                        uncheckedThumbColor = colors.muted,
                        uncheckedTrackColor = colors.panel2,
                        uncheckedBorderColor = colors.line,
                    ),
                )
            }
        }
    }
}

/**
 * Geri alinamayan eylem, gezinme dugmelerinden uzakta, kendi satirinda.
 * Eskiden gunluk basliginda "Tumu" dugmesinin bitisiginde ve ayni gorunumdeydi.
 */
@Composable
private fun DangerPanel(colors: FlightColors, actions: FlightActions) {
    // Iki dokunus: ilkinde soru sorar, dort saniye icinde onaylanmazsa geri alir.
    var confirming by remember { mutableStateOf(false) }
    LaunchedEffect(confirming) {
        if (confirming) {
            delay(4000)
            confirming = false
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (confirming) {
                "Günlük, ilerleme ve toplamlar silinecek. Emin misin?"
            } else {
                "Bütün ilerlemeyi ve günlüğü siler."
            },
            style = monoStyle(colors.muted, 10),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        DangerButton(
            text = if (confirming) "Evet, sil" else "Her şeyi sil",
            colors = colors,
        ) {
            if (confirming) {
                actions.onResetEverything()
                confirming = false
            } else {
                confirming = true
            }
        }
    }
}

@Composable
private fun LogPanel(
    state: FlightUiState,
    colors: FlightColors,
    onLogbook: () -> Unit,
) {
    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            PanelHead("Uçuş günlüğü", colors) {
                GhostButton("Tümünü gör", colors, onClick = onLogbook)
            }
            Spacer(Modifier.height(12.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("TOPLAM ODAK", style = labelStyle(colors.muted).copy(fontSize = 10.sp))
                    Text(formatHours(state.totalMs), style = monoStyle(colors.ink, 17))
                }
                Column(Modifier.weight(1f)) {
                    Text("KAT EDİLEN YOL", style = labelStyle(colors.muted).copy(fontSize = 10.sp))
                    Text("${formatNumber(state.totalKm)} km", style = monoStyle(colors.ink, 17))
                }
            }
            Spacer(Modifier.height(12.dp))

            if (state.log.isEmpty()) {
                Text(
                    "Henüz tamamlanmış uçuş yok. İlk rotanı bitirdiğinde buraya damga düşecek.",
                    style = monoStyle(colors.muted, 11),
                )
            } else {
                for (entry in state.log.take(8)) {
                    val parts = entry.routeKey.split("-")
                    val destCity = Airports[parts.getOrNull(1) ?: ""]?.city ?: parts.getOrNull(1).orEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.routeKey.replace("-", "→"), style = monoStyle(colors.amber, 12))
                        Spacer(Modifier.width(10.dp))
                        Text(destCity, style = monoStyle(colors.ink2, 12), modifier = Modifier.weight(1f))
                        Text(formatDate(entry.time), style = monoStyle(colors.muted, 11))
                    }
                    HorizontalDivider(color = colors.lineSoft)
                }
            }
        }
    }
}
