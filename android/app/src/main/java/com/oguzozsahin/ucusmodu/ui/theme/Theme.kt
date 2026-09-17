package com.oguzozsahin.ucusmodu.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.oguzozsahin.ucusmodu.data.ThemeChoice

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
    /** Kara dolgusu. */
    val land: Color,
    /** Kiyi cizgisi. */
    val coast: Color,
    val lake: Color,
    val night: Color,
    /** Geri alinamayan eylemler icin; baska hicbir yerde kullanilmaz. */
    val danger: Color,
    /**
     * Isi haritasinin tek renkli merdiveni: bostan yoguna dogru bes basamak.
     * Karanlik temada merdiven ters yone tirmanir — koyu zeminde "daha cok",
     * daha koyu degil daha parlak demektir.
     */
    val heat: List<Color>,
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
    globeA = Color(0xFFDCEAF4),
    globeB = Color(0xFFB4CCDF),
    grat = Color(0xFFC3CFDB),
    limb = Color(0xFFA9B8C7),
    land = Color(0xFFDFE3CE),
    coast = Color(0xFF9AA894),
    lake = Color(0xFFC2D7E6),
    night = Color(0x38102338),
    danger = Color(0xFFB3261E),
    heat = listOf(
        Color(0xFFE7ECF2),
        Color(0xFFF6E0BE),
        Color(0xFFE9BE7E),
        Color(0xFFCF9231),
        Color(0xFFA25908),
    ),
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
    globeA = Color(0xFF0E2136),
    globeB = Color(0xFF06121F),
    grat = Color(0xFF1F2F47),
    limb = Color(0xFF31445F),
    land = Color(0xFF1F3442),
    coast = Color(0xFF4A6C8C),
    lake = Color(0xFF0F2B42),
    night = Color(0x59000814),
    danger = Color(0xFFE0705F),
    heat = listOf(
        Color(0xFF1A2536),
        Color(0xFF4B3A21),
        Color(0xFF8B6526),
        Color(0xFFC5903B),
        Color(0xFFF2B457),
    ),
)

/** Acik temanin pembe hali. Masaustundeki `:root[data-theme="pink"]` ile ayni degerler. */
val PinkFlightColors = FlightColors(
    space = Color(0xFFF6DDE8),
    ground = Color(0xFFFBEFF4),
    panel = Color(0xFFFFFFFF),
    panel2 = Color(0xFFFDF5F9),
    line = Color(0xFFEFD3DF),
    lineSoft = Color(0xFFF6E4EC),
    ink = Color(0xFF2B1420),
    ink2 = Color(0xFF5B3547),
    muted = Color(0xFF8C6676),
    amber = Color(0xFFC2185B),
    amber2 = Color(0xFFE0528D),
    teal = Color(0xFF8E3B7E),
    globeA = Color(0xFFF7D9E6),
    globeB = Color(0xFFEBB4CC),
    grat = Color(0xFFEFC6D8),
    limb = Color(0xFFD9A3BC),
    land = Color(0xFFFFF6F9),
    coast = Color(0xFFD08AAB),
    lake = Color(0xFFF0C6D9),
    night = Color(0x334A1434),
    danger = Color(0xFFB3261E),
    heat = listOf(
        Color(0xFFF8E7EF),
        Color(0xFFF6C8DB),
        Color(0xFFEE9CBF),
        Color(0xFFDA5E93),
        Color(0xFFAD1857),
    ),
)

val LocalFlightColors = staticCompositionLocalOf { LightFlightColors }

/** Kisayol: MaterialTheme.flight */
val MaterialTheme.flight: FlightColors
    @Composable
    get() = LocalFlightColors.current

@Composable
fun UcusModuTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (choice) {
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
        ThemeChoice.LIGHT, ThemeChoice.PINK -> false
        ThemeChoice.DARK -> true
    }
    val colors = when {
        choice == ThemeChoice.PINK -> PinkFlightColors
        darkTheme -> DarkFlightColors
        else -> LightFlightColors
    }

    // Sistem cubuklari XML temasindan geliyordu: pembe temada gri-mavi, sistem
    // acikken Koyu secilince de acik kaliyordu. Artik secilen temayi izliyor.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            @Suppress("DEPRECATION")
            window.statusBarColor = colors.ground.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = colors.ground.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

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
