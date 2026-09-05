package com.oguzozsahin.ucusmodu.ui.window

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Gunes yuksekligine gore gokyuzu, deniz, kara ve bulut renkleri.
 *
 * Renkler elle secilmis birkac anahtar kareden dogrusal olarak turetilir; boylece
 * ogleden gun batimina, alacakaranliktan geceye gecis kesintisiz olur.
 */
data class SkyPalette(
    /** Basucu (ekranin en ustu) rengi. */
    val zenith: Color,
    /** Ufuk cizgisindeki gokyuzu rengi. */
    val horizon: Color,
    val sea: Color,
    val land: Color,
    val cloudLit: Color,
    val cloudShade: Color,
    /** Gunesin bulundugu yonde ufka vuran sicak isik. */
    val glow: Color,
    val glowStrength: Float,
    /** 0 gunduz, 1 tam karanlik: yildizlar ve sehir isiklari bununla acilir. */
    val night: Float,
)

private class Keyframe(
    val elevation: Double,
    val zenith: Long,
    val horizon: Long,
    val sea: Long,
    val land: Long,
    val cloudLit: Long,
    val cloudShade: Long,
    val glow: Long,
    val glowStrength: Float,
    val night: Float,
)

// Gunes yuksekligi buyukten kucuge siralanir.
private val KEYFRAMES = listOf(
    Keyframe(60.0, 0xFF14346E, 0xFF93C1E8, 0xFF1E4B78, 0xFF3F5C3C, 0xFFFFFFFF, 0xFFC6D6E6, 0xFFFFF3D8, 0.10f, 0f),
    Keyframe(12.0, 0xFF1B4F9C, 0xFFAECEEA, 0xFF23507C, 0xFF425F3E, 0xFFF8FBFF, 0xFFBACBDD, 0xFFFFE8B0, 0.22f, 0f),
    Keyframe(3.0, 0xFF2B5286, 0xFFEFB878, 0xFF2A4A66, 0xFF4A4C34, 0xFFFFE2C0, 0xFFB79178, 0xFFFFC073, 0.62f, 0f),
    Keyframe(-1.0, 0xFF1D2E56, 0xFFE8763E, 0xFF1B2E42, 0xFF2E3128, 0xFFEFAE7C, 0xFF8A6252, 0xFFFF8A3C, 0.90f, 0.15f),
    Keyframe(-6.0, 0xFF101B3A, 0xFFB8452F, 0xFF0E1826, 0xFF171A18, 0xFFAE6E58, 0xFF5A3B36, 0xFFE04E24, 0.72f, 0.55f),
    Keyframe(-12.0, 0xFF070C1D, 0xFF3E2340, 0xFF070C14, 0xFF0C0E10, 0xFF4E3648, 0xFF241A24, 0xFF7A2C3C, 0.34f, 0.92f),
    Keyframe(-18.0, 0xFF03060E, 0xFF0E1526, 0xFF03050A, 0xFF070809, 0xFF171D29, 0xFF0B0E15, 0xFF1A2038, 0.08f, 1f),
    Keyframe(-90.0, 0xFF02040A, 0xFF0A1020, 0xFF020408, 0xFF060708, 0xFF121721, 0xFF090C12, 0xFF141A2E, 0.05f, 1f),
)

/** Gunesin yerel yataydan yuksekligine (derece) karsilik gelen renk takimi. */
fun paletteFor(sunElevationDeg: Double): SkyPalette {
    val first = KEYFRAMES.first()
    if (sunElevationDeg >= first.elevation) return first.toPalette()
    for (i in 0 until KEYFRAMES.size - 1) {
        val a = KEYFRAMES[i]
        val b = KEYFRAMES[i + 1]
        if (sunElevationDeg <= a.elevation && sunElevationDeg >= b.elevation) {
            val span = a.elevation - b.elevation
            val t = if (span < 1e-9) 0f else ((a.elevation - sunElevationDeg) / span).toFloat()
            return a.toPalette().blend(b.toPalette(), t)
        }
    }
    return KEYFRAMES.last().toPalette()
}

private fun Keyframe.toPalette() = SkyPalette(
    zenith = Color(zenith),
    horizon = Color(horizon),
    sea = Color(sea),
    land = Color(land),
    cloudLit = Color(cloudLit),
    cloudShade = Color(cloudShade),
    glow = Color(glow),
    glowStrength = glowStrength,
    night = night,
)

private fun SkyPalette.blend(other: SkyPalette, t: Float) = SkyPalette(
    zenith = lerp(zenith, other.zenith, t),
    horizon = lerp(horizon, other.horizon, t),
    sea = lerp(sea, other.sea, t),
    land = lerp(land, other.land, t),
    cloudLit = lerp(cloudLit, other.cloudLit, t),
    cloudShade = lerp(cloudShade, other.cloudShade, t),
    glow = lerp(glow, other.glow, t),
    glowStrength = glowStrength + (other.glowStrength - glowStrength) * t,
    night = night + (other.night - night) * t,
)
