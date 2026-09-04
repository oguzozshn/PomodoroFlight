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

/** Kure cerceveden kucuk gorunecek kadar uzaklasabilir, sehir olceginde yakinlasabilir. */
const val GLOBE_MIN_ZOOM = 0.55f
const val GLOBE_MAX_ZOOM = 18f

/** Rotayi cerceveye oturtan varsayilan olcek. */
const val GLOBE_FIT_ZOOM = 1f

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

    var zoom by mutableFloatStateOf(GLOBE_FIT_ZOOM)
        private set

    /** Kullanici kureyi kendisi oynatmadiysa dogru. */
    var untouched by mutableStateOf(true)
        private set

    fun rotateBy(deltaLat: Float, deltaLon: Float) {
        untouched = false
        lat = (lat + deltaLat).coerceIn(-89f, 89f)
        lon = wrapLongitude(lon + deltaLon)
    }

    /** Kamerayi dogrudan verilen noktaya tasir (sabitlemeli yakinlastirma icin). */
    fun moveTo(newLat: Double, newLon: Double) {
        untouched = false
        lat = newLat.toFloat().coerceIn(-89f, 89f)
        lon = wrapLongitude(newLon.toFloat())
    }

    fun zoomBy(factor: Float) {
        if (factor == 1f) return
        untouched = false
        zoom = (zoom * factor).coerceIn(GLOBE_MIN_ZOOM, GLOBE_MAX_ZOOM)
    }

    /**
     * Kolun surdugu dogrudan olcek. Adi setZoom olamaz: "var zoom" zaten
     * JVM'de setZoom(Float) uretiyor ve imzalar cakisiyor.
     */
    fun snapZoomTo(value: Float) {
        untouched = false
        zoom = value.coerceIn(GLOBE_MIN_ZOOM, GLOBE_MAX_ZOOM)
    }

    fun toggleZoom() {
        untouched = false
        zoom = if (zoom < 2f) 3.5f else GLOBE_FIT_ZOOM
    }

    /** Dugmelerle kademeli yakinlastirma. */
    suspend fun animateZoomTo(target: Float, durationMs: Int = 240) {
        untouched = false
        val from = zoom
        val to = target.coerceIn(GLOBE_MIN_ZOOM, GLOBE_MAX_ZOOM)
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMs, easing = FastOutSlowInEasing),
        ) { t, _ ->
            zoom = from + (to - from) * t
        }
        zoom = to
    }

    /** Hedefe yumusak bir donusle gider. */
    suspend fun flyTo(
        targetLat: Double,
        targetLon: Double,
        targetZoom: Float = zoom,
        resetTouch: Boolean = false,
        durationMs: Int = 620,
    ) {
        if (!resetTouch) untouched = false
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

    val canZoomIn: Boolean get() = zoom < GLOBE_MAX_ZOOM - 0.01f
    val canZoomOut: Boolean get() = zoom > GLOBE_MIN_ZOOM + 0.01f
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
