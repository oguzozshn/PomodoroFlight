package com.oguzozsahin.ucusmodu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import com.oguzozsahin.ucusmodu.data.Airport
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.PlaceRegistry
import com.oguzozsahin.ucusmodu.data.Places

/** Uygulamanin ucus gunu akisi: terminal -> rota -> ucus. */
enum class Stage { WELCOME, ROUTE, FLIGHT }

private const val STAGE_MS = 420

@Composable
fun FlightRoot(
    state: FlightUiState,
    actions: FlightActions,
    modifier: Modifier = Modifier,
) {
    // Sayac zaten isliyorsa uygulama dogrudan ucus ekranina acilir.
    var stage by remember { mutableStateOf(if (state.running) Stage.FLIGHT else Stage.WELCOME) }

    // Ucus yapilabilen yerler bir kez okunur; liste hazir olunca kure onlari isaretler.
    val context = LocalContext.current
    var places by remember { mutableStateOf<List<Airport>>(PlaceRegistry.all()) }
    LaunchedEffect(Unit) {
        Places.ensureLoaded(context)
        places = PlaceRegistry.all()
    }

    BackHandler(enabled = stage != Stage.WELCOME) {
        stage = if (stage == Stage.FLIGHT) Stage.ROUTE else Stage.WELCOME
    }

    AnimatedContent(
        targetState = stage,
        modifier = modifier.fillMaxSize(),
        transitionSpec = {
            val forward = targetState.ordinal > initialState.ordinal
            val direction = if (forward) 1 else -1
            val spec = tween<IntOffset>(STAGE_MS, easing = FastOutSlowInEasing)
            (
                slideInHorizontally(spec) { width -> direction * width / 4 } +
                    fadeIn(tween(260))
                ).togetherWith(
                slideOutHorizontally(spec) { width -> -direction * width / 4 } +
                    fadeOut(tween(200)),
            )
        },
        label = "asama",
    ) { current ->
        when (current) {
            Stage.WELCOME -> WelcomeScreen(
                state = state,
                onStartJourney = { stage = Stage.ROUTE },
                onResume = { stage = Stage.FLIGHT },
            )

            Stage.ROUTE -> RouteScreen(
                state = state,
                actions = actions,
                places = places,
                onBack = { stage = Stage.WELCOME },
                onDeparture = {
                    actions.onStart()
                    stage = Stage.FLIGHT
                },
            )

            Stage.FLIGHT -> FlightScreen(
                state = state,
                actions = actions,
                onChangeRoute = { stage = Stage.ROUTE },
            )
        }
    }
}
