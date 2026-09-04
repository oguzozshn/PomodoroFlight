package com.oguzozsahin.ucusmodu.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.ui.theme.LocalFlightColors
import kotlinx.coroutines.delay

private const val COUNTDOWN_FROM = 3

/**
 * Uygulamanin kabugu: ucus ekrani ve uzerine binen kalkis geri sayimi.
 * Geri sayim yalnizca surec basina bir kez, otomatik kalkis aciksa calisir.
 */
@Composable
fun FlightRoot(
    state: FlightUiState,
    actions: FlightActions,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFlightColors.current
    var armed by remember { mutableStateOf(true) }
    var countdown by remember { mutableIntStateOf(0) }

    LaunchedEffect(armed) {
        if (!armed) return@LaunchedEffect
        if (!actions.consumeAutoStart()) return@LaunchedEffect
        for (value in COUNTDOWN_FROM downTo 1) {
            countdown = value
            delay(1000L)
        }
        countdown = 0
        actions.onToggle()
    }

    Box(modifier = modifier.fillMaxSize()) {
        FlightScreen(state = state, actions = actions)

        AnimatedVisibility(
            visible = countdown > 0,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(260)),
        ) {
            TakeoffCountdown(
                route = state.route,
                seconds = countdown,
                colors = colors,
            ) {
                countdown = 0
                armed = false
            }
        }
    }
}
