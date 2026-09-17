package com.oguzozsahin.ucusmodu.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.oguzozsahin.ucusmodu.ui.theme.LocalFlightColors
import com.oguzozsahin.ucusmodu.ui.window.WindowScreen

/** Ucus sekmesinin akisi: terminal -> rota -> bilet -> ucus. */
enum class Stage { WELCOME, ROUTE, TICKET, FLIGHT, WINDOW }

private const val STAGE_MS = 420

/** Alt cubugun gizlendigi, tam ekran kalmasi gereken asamalar. */
private val FullScreenStages = setOf(Stage.TICKET, Stage.WINDOW)

@Composable
fun FlightRoot(
    state: FlightUiState,
    actions: FlightActions,
    theme: ThemeChoice,
    onTheme: (ThemeChoice) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFlightColors.current

    // Gercek bir gecmis tutulur. Once sabit bir "ebeveyn" tablosu vardi ve
    // geri tusu nereden gelindigini bilmiyordu: terminalden ucusa devam edip
    // geri basinca rota secimine dusuluyor, ucustan acilan mil bildiriminden
    // geri basinca terminale atiliyordu.
    val stack = remember {
        mutableStateListOf(if (state.running) Stage.FLIGHT else Stage.WELCOME)
    }
    val stage = stack.last()

    // Ucus gunlugu ayri sekme: ucus yigini sekme degisince oldugu gibi kalir.
    var tab by remember { mutableStateOf(FlightTab.FLIGHT) }

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

    // Geri tusu once gunluk ya da ayarlar sekmesinden ucusa, sonra ucus yigininda geri sarar.
    BackHandler(enabled = tab != FlightTab.FLIGHT || stack.size > 1) {
        if (tab != FlightTab.FLIGHT) tab = FlightTab.FLIGHT else back()
    }

    val showBar = tab != FlightTab.FLIGHT || stage !in FullScreenStages

    val body: @Composable () -> Unit = {
        Crossfade(targetState = tab, animationSpec = tween(220), label = "sekme") { current ->
            when (current) {
                FlightTab.LOGBOOK -> LogbookScreen(state = state)
                FlightTab.SETTINGS -> SettingsScreen(
                    state = state,
                    actions = actions,
                    theme = theme,
                    onTheme = onTheme,
                )
                FlightTab.FLIGHT -> FlightStages(
                    stage = stage,
                    state = state,
                    actions = actions,
                    places = places,
                    goTo = ::goTo,
                    back = ::back,
                    departure = ::departure,
                    openLogbook = { tab = FlightTab.LOGBOOK },
                )
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Telefonda alt cubuk, genis ekranda yan ray.
        if (maxWidth >= TwoPaneMinWidth) {
            Row(Modifier.fillMaxSize()) {
                if (showBar) FlightTabRail(tab, colors) { tab = it }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) { body() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                ) { body() }
                if (showBar) FlightTabBar(tab, colors) { tab = it }
            }
        }
    }
}

/** Ucus sekmesinin asama yigini. */
@Composable
private fun FlightStages(
    stage: Stage,
    state: FlightUiState,
    actions: FlightActions,
    places: List<Airport>,
    goTo: (Stage) -> Unit,
    back: () -> Unit,
    departure: () -> Unit,
    openLogbook: () -> Unit,
) {
    AnimatedContent(
        targetState = stage,
        modifier = Modifier.fillMaxSize(),
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
                onLogbook = openLogbook,
            )

            Stage.ROUTE -> RouteScreen(
                state = state,
                actions = actions,
                places = places,
                onBack = back,
                onDeparture = { goTo(Stage.TICKET) },
                onResume = { goTo(Stage.FLIGHT) },
            )

            Stage.TICKET -> TicketScreen(
                ticket = remember(state.route.key, state.done, state.focusMin) {
                    issueTicket(state)
                },
                onBack = back,
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
                onFlightEnded = { goTo(Stage.ROUTE) },
                onFlyAgain = {
                    // Inmis ucus birakilir; bilet yeniden kesilince sifirdan baslar.
                    actions.onRoute(state.route.key)
                    goTo(Stage.TICKET)
                },
                onBack = back,
            )

            Stage.WINDOW -> WindowScreen(
                state = state,
                actions = actions,
                onExit = back,
            )
        }
    }
}
