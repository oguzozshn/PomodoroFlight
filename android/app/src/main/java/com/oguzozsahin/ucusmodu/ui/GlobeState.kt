package com.oguzozsahin.ucusmodu.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.oguzozsahin.ucusmodu.data.LatLon

const val GLOBE_MIN_ZOOM = 1f
const val GLOBE_MAX_ZOOM = 9f

/**
 * Kullanicinin surdugu kamera: kurenin baktigi nokta ve yakinlastirma.
 * Rota ekraninda kure bunu izler; diger ekranlarda kamera rotaya kilitlidir.
 */
@Stable
class GlobeState(initialLat: Float, initialLon: Float) {

    var lat by mutableFloatStateOf(initialLat)
        private set

    var lon by mutableFloatStateOf(initialLon)
        private set

    var zoom by mutableFloatStateOf(GLOBE_MIN_ZOOM)
        private set

    /** Kullanici kureyi kendisi oynatmadiysa dogru. */
    var untouched by mutableStateOf(true)
        private set

    fun rotateBy(deltaLat: Float, deltaLon: Float) {
        untouched = false
        lat = (lat + deltaLat).coerceIn(-89f, 89f)
        lon = wrapLongitude(lon + deltaLon)
    }

    fun zoomBy(factor: Float) {
        if (factor == 1f) return
        untouched = false
        zoom = (zoom * factor).coerceIn(GLOBE_MIN_ZOOM, GLOBE_MAX_ZOOM)
    }

    fun toggleZoom() {
        untouched = false
        zoom = if (zoom < 2f) 3f else GLOBE_MIN_ZOOM
    }

    /** Hedefe yumusak bir donusle gider. */
    suspend fun flyTo(
        targetLat: Double,
        targetLon: Double,
        targetZoom: Float = zoom,
        resetTouch: Boolean = false,
        durationMs: Int = 620,
    ) {
        val fromLat = lat
        val fromLon = lon
        val fromZoom = zoom
        var delta = targetLon.toFloat() - fromLon
        if (delta > 180f) delta -= 360f
        if (delta < -180f) delta += 360f

        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        ) { t, _ ->
            lat = fromLat + (targetLat.toFloat() - fromLat) * t
            lon = wrapLongitude(fromLon + delta * t)
            zoom = fromZoom + (targetZoom - fromZoom) * t
        }
        lat = targetLat.toFloat()
        lon = wrapLongitude(targetLon.toFloat())
        zoom = targetZoom
        if (resetTouch) untouched = true
    }

    val center: LatLon get() = LatLon(lat.toDouble(), lon.toDouble())
}

private fun wrapLongitude(value: Float): Float {
    var v = value
    while (v > 180f) v -= 360f
    while (v < -180f) v += 360f
    return v
}

@Composable
fun rememberGlobeState(center: LatLon): GlobeState =
    remember { GlobeState(center.lat.toFloat(), center.lon.toFloat()) }
