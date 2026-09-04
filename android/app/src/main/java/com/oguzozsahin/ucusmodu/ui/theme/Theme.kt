package com.oguzozsahin.ucusmodu.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Web surumundeki CSS degiskenlerinin birebir karsiligi. */
@Immutable
data class FlightColors(
    val space: Color,
    val ground: Color,
    val panel: Color,
    val panel2: Color,
    val line: Color,
    val lineSoft: Color,
    val ink: Color,
    val ink2: Color,
    val muted: Color,
    val amber: Color,
    val amber2: Color,
    val teal: Color,
    val globeA: Color,
    val globeB: Color,
    val grat: Color,
    val limb: Color,
    val land: Color,
    val night: Color,
)

val LightFlightColors = FlightColors(
    space = Color(0xFFDCE3EA),
    ground = Color(0xFFEDF0F4),
    panel = Color(0xFFFFFFFF),
    panel2 = Color(0xFFF4F6F9),
    line = Color(0xFFD2DAE3),
    lineSoft = Color(0xFFE3E9EF),
    ink = Color(0xFF14202C),
    ink2 = Color(0xFF3B4A5A),
    muted = Color(0xFF66757F),
    amber = Color(0xFFB5670C),
    amber2 = Color(0xFFD98A26),
    teal = Color(0xFF0E6B85),
    globeA = Color(0xFFE7EDF3),
    globeB = Color(0xFFD5DFE9),
    grat = Color(0xFFC3CFDB),
    limb = Color(0xFFA9B8C7),
    land = Color(0xFF7C8FA3),
    night = Color(0x38102338),
)

val DarkFlightColors = FlightColors(
    space = Color(0xFF060B13),
    ground = Color(0xFF080E18),
    panel = Color(0xFF0F1725),
    panel2 = Color(0xFF131E2E),
    line = Color(0xFF223047),
    lineSoft = Color(0xFF1A2536),
    ink = Color(0xFFE6EDF7),
    ink2 = Color(0xFFB7C6D8),
    muted = Color(0xFF7F93AE),
    amber = Color(0xFFF2B457),
    amber2 = Color(0xFFFFCC7A),
    teal = Color(0xFF58A8C9),
    globeA = Color(0xFF101C2E),
    globeB = Color(0xFF0A121E),
    grat = Color(0xFF1F2F47),
    limb = Color(0xFF31445F),
    land = Color(0xFF4C6E93),
    night = Color(0x59000814),
)

val LocalFlightColors = staticCompositionLocalOf { LightFlightColors }

/** Kisayol: MaterialTheme.flight */
val MaterialTheme.flight: FlightColors
    @Composable
    get() = LocalFlightColors.current

@Composable
fun UcusModuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkFlightColors else LightFlightColors

    val scheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.amber,
            onPrimary = colors.ground,
            secondary = colors.teal,
            background = colors.ground,
            onBackground = colors.ink,
            surface = colors.panel,
            onSurface = colors.ink,
            surfaceVariant = colors.panel2,
            onSurfaceVariant = colors.ink2,
            outline = colors.line,
        )
    } else {
        lightColorScheme(
            primary = colors.amber,
            onPrimary = Color.White,
            secondary = colors.teal,
            background = colors.ground,
            onBackground = colors.ink,
            surface = colors.panel,
            onSurface = colors.ink,
            surfaceVariant = colors.panel2,
            onSurfaceVariant = colors.ink2,
            outline = colors.line,
        )
    }

    CompositionLocalProvider(LocalFlightColors provides colors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = flightTypography(colors),
            content = content,
        )
    }
}
