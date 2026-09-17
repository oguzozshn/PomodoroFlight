package com.oguzozsahin.ucusmodu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.ThemeChoice
import com.oguzozsahin.ucusmodu.data.upperTr
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.LocalFlightColors
import com.oguzozsahin.ucusmodu.ui.theme.labelStyle
import com.oguzozsahin.ucusmodu.ui.theme.monoStyle
import kotlinx.coroutines.delay

/**
 * Ayarlar sekmesi.
 *
 * Ayarlar eskiden ucus ekraninin dibindeydi; oraya ancak bir ucus baslatinca
 * ulasiliyordu, uygulamayi yeni kuran biri temayi bile degistiremiyordu.
 * Sureler burada degil: ucustan once rota ekraninda secilir, ucusta kilitlidir.
 */
@Composable
fun SettingsScreen(
    state: FlightUiState,
    actions: FlightActions,
    theme: ThemeChoice,
    onTheme: (ThemeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFlightColors.current

    ReadableColumn(
        background = colors.ground,
        modifier = modifier,
        bottomPadding = 28.dp,
    ) {
        Text(
            "AYARLAR",
            style = labelStyle(colors.ink2).copy(fontSize = 13.sp),
            modifier = Modifier.padding(top = 14.dp, bottom = 2.dp),
        )
        AppearancePanel(theme, colors, onTheme)
        CounterPanel(state, colors, actions)
        PassengerPanel(state, colors, actions)
        DangerPanel(colors, actions)
    }
}

@Composable
private fun AppearancePanel(
    theme: ThemeChoice,
    colors: FlightColors,
    onTheme: (ThemeChoice) -> Unit,
) {
    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            PanelHead("Görünüm", colors)
            Spacer(Modifier.height(12.dp))
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
                            .padding(vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CounterPanel(state: FlightUiState, colors: FlightColors, actions: FlightActions) {
    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            PanelHead("Sayaç", colors)
            Spacer(Modifier.height(10.dp))
            SettingSwitch("Kabin sesi", state.sound, colors, actions.onSound)
            SettingSwitch("Molalar kendiliğinden başlasın", state.autoBreak, colors, actions.onAutoBreak)
            Spacer(Modifier.height(6.dp))
            Text(
                "Odak, mola ve uzun mola süreleri bilet kesmeden önce rota ekranında ayarlanır; " +
                    "uçuş sürerken kilitlidir.",
                style = monoStyle(colors.muted, 10),
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    label: String,
    checked: Boolean,
    colors: FlightColors,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = monoStyle(colors.ink2, 12), modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
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

@Composable
private fun PassengerPanel(state: FlightUiState, colors: FlightColors, actions: FlightActions) {
    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            PanelHead("Biniş kartı", colors)
            Spacer(Modifier.height(10.dp))
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
            Spacer(Modifier.height(6.dp))
            Text("Boş bırakılırsa bilette \"YOLCU\" yazar.", style = monoStyle(colors.muted, 10))
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
        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
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
