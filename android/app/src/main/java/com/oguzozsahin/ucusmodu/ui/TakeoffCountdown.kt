package com.oguzozsahin.ucusmodu.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.data.Route
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.labelStyle
import com.oguzozsahin.ucusmodu.ui.theme.monoStyle

/**
 * Acilista otomatik kalkis oncesi vazgecme payi. Ekranin herhangi bir yerine
 * dokunmak da kalkisi iptal eder.
 */
@Composable
fun TakeoffCountdown(
    route: Route,
    seconds: Int,
    colors: FlightColors,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground.copy(alpha = 0.95f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onCancel,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "KALKIŞA",
                style = labelStyle(colors.muted).copy(fontSize = 13.sp, letterSpacing = 4.sp),
            )
            Spacer(Modifier.height(14.dp))

            AnimatedContent(
                targetState = seconds,
                transitionSpec = {
                    (scaleIn(tween(220), initialScale = 1.35f) + fadeIn(tween(160)))
                        .togetherWith(scaleOut(tween(220), targetScale = 0.75f) + fadeOut(tween(160)))
                },
                label = "kalkis-sayaci",
            ) { value ->
                Text(
                    value.toString(),
                    style = monoStyle(colors.amber, 96, FontWeight.Light),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                "${route.origin.code} → ${route.dest.code}",
                style = monoStyle(colors.ink, 15, FontWeight.Medium),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${route.origin.city} — ${route.dest.city}",
                style = monoStyle(colors.muted, 12),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(30.dp))
            FlightButton(
                text = "Vazgeç",
                colors = colors,
                onClick = onCancel,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "ekrana dokunmak da kalkışı iptal eder",
                style = monoStyle(colors.muted, 10),
            )
        }
    }
}
