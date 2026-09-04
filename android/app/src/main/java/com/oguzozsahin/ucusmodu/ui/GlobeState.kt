package com.oguzozsahin.ucusmodu.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** Kurenin en fazla ne kadar yakinlastirilabilecegi. */
private const val MAX_ZOOM = 8f
private const val MIN_ZOOM = 1f

/**
 * Kullanicinin kureye verdigi sapma: rota merkezinin uzerine binen donme ve
 * yakinlastirma. Sifirlaninca kure yeniden rotayi takip eder.
 */
@Stable
class GlobeState {

    var latOffset by mutableFloatStateOf(0f)
        internal set

    var lonOffset by mutableFloatStateOf(0f)
        internal set

    var zoom by mutableFloatStateOf(1f)
        internal set

    /** Kullanici kureye dokunmus mu (geri ortala dugmesi bunun icin). */
    val adjusted: Boolean
        get() = latOffset != 0f || lonOffset != 0f || zoom != 1f

    fun rotateBy(deltaLat: Float, deltaLon: Float) {
        latOffset = (latOffset + deltaLat).coerceIn(-120f, 120f)
        lonOffset += deltaLon
    }

    fun zoomBy(factor: Float) {
        zoom = (zoom * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    fun toggleZoom() {
        zoom = if (zoom < 2f) 3f else MIN_ZOOM
    }

    /** Rotanin merkezine yumusak bir donusle geri gider. */
    suspend fun recenter() {
        val fromLat = latOffset
        val fromLon = lonOffset
        val fromZoom = zoom
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = 520, easing = FastOutSlowInEasing),
        ) { t, _ ->
            latOffset = fromLat * (1f - t)
            lonOffset = fromLon * (1f - t)
            zoom = fromZoom + (MIN_ZOOM - fromZoom) * t
        }
        latOffset = 0f
        lonOffset = 0f
        zoom = MIN_ZOOM
    }
}

@Composable
fun rememberGlobeState(): GlobeState = remember { GlobeState() }
