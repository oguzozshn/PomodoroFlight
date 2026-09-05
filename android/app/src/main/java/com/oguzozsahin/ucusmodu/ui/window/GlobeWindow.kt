package com.oguzozsahin.ucusmodu.ui.window

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.oguzozsahin.ucusmodu.data.Airport
import com.oguzozsahin.ucusmodu.data.PlaceRegistry
import com.oguzozsahin.ucusmodu.data.Places
import com.oguzozsahin.ucusmodu.data.Route
import com.oguzozsahin.ucusmodu.ui.GlobeMap
import com.oguzozsahin.ucusmodu.ui.rememberGlobeState
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import kotlin.math.roundToInt

/** Kurenin ucagi izlerken durdugu olcek. */
private const val FOLLOW_ZOOM = 4.2f

/**
 * Giris ekranindaki kurenin ucusu izleyen hali: kamera ucagin uzerinde durur,
 * kullanici kureyi elleyince serbest kalir, biraktiginda ucaga geri doner.
 */
@Composable
fun GlobeWindow(
    route: Route,
    fraction: Float,
    colors: FlightColors,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var places by remember { mutableStateOf<List<Airport>>(PlaceRegistry.all()) }
    LaunchedEffect(Unit) {
        Places.ensureLoaded(context)
        places = PlaceRegistry.all()
    }

    val here = route.pointAt(fraction.toDouble())
    val state = rememberGlobeState(here)

    LaunchedEffect(Unit) {
        state.flyTo(here.lat, here.lon, targetZoom = FOLLOW_ZOOM, resetTouch = true, durationMs = 1100)
    }

    // Ucak gorunur sekilde ilerledikce kamera pesinden gider; el degdiyse karismaz.
    val step = (here.lat * 20).roundToInt() to (here.lon * 20).roundToInt()
    LaunchedEffect(step) {
        if (!state.untouched) return@LaunchedEffect
        state.flyTo(here.lat, here.lon, targetZoom = state.zoom, resetTouch = true, durationMs = 900)
    }

    GlobeMap(
        route = route,
        fraction = fraction,
        colors = colors,
        interactive = true,
        state = state,
        places = places,
        highlighted = setOf(route.origin.code, route.dest.code),
        modifier = modifier,
    )
}
