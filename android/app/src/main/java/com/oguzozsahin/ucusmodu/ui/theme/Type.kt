package com.oguzozsahin.ucusmodu.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.R

val Barlow = FontFamily(
    Font(R.font.barlow_regular, FontWeight.Normal),
    Font(R.font.barlow_medium, FontWeight.Medium),
)

val BarlowCondensed = FontFamily(
    Font(R.font.barlow_condensed_medium, FontWeight.Medium),
    Font(R.font.barlow_condensed_semibold, FontWeight.SemiBold),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_light, FontWeight.Light),
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
)

/** Panel basliklari: sikistirilmis, buyuk harf, genis harf araligi. */
fun headStyle(color: androidx.compose.ui.graphics.Color) = TextStyle(
    fontFamily = BarlowCondensed,
    fontWeight = FontWeight.SemiBold,
    fontSize = 15.sp,
    letterSpacing = 2.sp,
    color = color,
)

fun labelStyle(color: androidx.compose.ui.graphics.Color) = TextStyle(
    fontFamily = BarlowCondensed,
    fontWeight = FontWeight.Medium,
    fontSize = 12.sp,
    letterSpacing = 1.6.sp,
    color = color,
)

fun monoStyle(
    color: androidx.compose.ui.graphics.Color,
    size: Int = 13,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    fontFamily = PlexMono,
    fontWeight = weight,
    fontSize = size.sp,
    color = color,
)

internal fun flightTypography(colors: FlightColors) = Typography(
    bodyLarge = TextStyle(fontFamily = Barlow, fontSize = 15.sp, color = colors.ink2),
    bodyMedium = TextStyle(fontFamily = Barlow, fontSize = 14.sp, color = colors.ink2),
    bodySmall = TextStyle(fontFamily = Barlow, fontSize = 13.sp, color = colors.muted),
    titleMedium = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        letterSpacing = 2.sp,
        color = colors.ink,
    ),
    labelLarge = TextStyle(
        fontFamily = BarlowCondensed,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 1.5.sp,
        color = colors.ink2,
    ),
)
