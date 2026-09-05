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
import androidx.compose.runtime.mutableStateListOf
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
import com.oguzozsahin.ucusmodu.data.ThemeChoice
import com.oguzozsahin.ucusmodu.data.issueTicket
import com.oguzozsahin.ucusmodu.ui.window.WindowScreen

/** Uygulamanin ucus gunu akisi: terminal -> rota -> ucus. */
enum class Stage { WELCOME, ROUTE, TICKET, FLIGHT, WINDOW, LOGBOOK }

private const val STAGE_MS = 420

@Composable
fun FlightRoot(
    state: FlightUiState,
    actions: FlightActions,
    theme: ThemeChoice,
    onTheme: (ThemeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Gercek bir gecmis tutulur. Once sabit bir "ebeveyn" tablosu vardi ve
    // geri tusu nereden gelindigini bilmiyordu: terminalden ucusa devam edip
    // geri basinca rota secimine dusuluyor, ucustan acilan mil bildiriminden
    // geri basinca terminale atiliyordu.
    val stack = remember {
        mutableStateListOf(if (state.running) Stage.FLIGHT else Stage.WELCOME)
    }
    val stage = stack.last()

    /** Ekran zaten yiginda varsa oraya kadar geri sarilir, yoksa ustune binilir. */
    fun goTo(next: Stage) {
        val index = stack.indexOf(next)
        if (index >= 0) {
            while (stack.lastIndex > index) stack.removeAt(stack.lastIndex)
        } else {
            stack.add(next)
        }
    }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
    }

    /** Bilet koparilinca yigindan dusurulur: geri tusu yirtik bilete donmesin. */
    fun departure() {
        back()
        goTo(Stage.FLIGHT)
    }

    // Ucus yapilabilen yerler bir kez okunur; liste hazir olunca kure onlari isaretler.
    val context = LocalContext.current
    var places by remember { mutableStateOf<List<Airport>>(PlaceRegistry.all()) }
    LaunchedEffect(Unit) {
        Places.ensureLoaded(context)
        places = PlaceRegistry.all()
    }

    BackHandler(enabled = stack.size > 1) { back() }

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
                onStartJourney = { goTo(Stage.ROUTE) },
                onResume = { goTo(Stage.FLIGHT) },
                onLogbook = { goTo(Stage.LOGBOOK) },
            )

            Stage.ROUTE -> RouteScreen(
                state = state,
                actions = actions,
                places = places,
                onBack = { back() },
                onDeparture = { goTo(Stage.TICKET) },
            )

            Stage.TICKET -> TicketScreen(
                ticket = remember(state.route.key, state.done, state.focusMin) {
                    issueTicket(state)
                },
                onBack = { back() },
                onPrint = actions.onTicketPrinted,
                onTearTick = actions.onTearTick,
                onDeparture = {
                    actions.onStart()
                    departure()
                },
            )

            Stage.FLIGHT -> FlightScreen(
                state = state,
                actions = actions,
                onChangeRoute = { goTo(Stage.ROUTE) },
                onWindow = { goTo(Stage.WINDOW) },
                onLogbook = { goTo(Stage.LOGBOOK) },
                onBack = { back() },
                theme = theme,
                onTheme = onTheme,
            )

            Stage.WINDOW -> WindowScreen(
                state = state,
                actions = actions,
                onExit = { back() },
            )

            Stage.LOGBOOK -> LogbookScreen(
                state = state,
                onBack = { back() },
            )
        }
    }
}
