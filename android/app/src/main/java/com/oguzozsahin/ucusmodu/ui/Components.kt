package com.oguzozsahin.ucusmodu.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.ui.theme.BarlowCondensed
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.headStyle
import com.oguzozsahin.ucusmodu.ui.theme.labelStyle

val PanelShape = RoundedCornerShape(4.dp)

/** Web surumundeki ".panel": ince cerceveli, koseleri az yuvarlatilmis kart. */
@Composable
fun Panel(
    colors: FlightColors,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.panel, PanelShape)
            .border(1.dp, colors.line, PanelShape),
        content = content,
    )
}

@Composable
fun PanelHead(
    title: String,
    colors: FlightColors,
    modifier: Modifier = Modifier,
    trailing: @Composable (RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = headStyle(colors.ink))
        Spacer(Modifier.width(10.dp))
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            trailing?.invoke(this)
        }
    }
}

/** Ana eylem dugmesi; [primary] olan kalkis/beklet dugmesidir. */
@Composable
fun FlightButton(
    text: String,
    colors: FlightColors,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val background = if (primary) colors.amber.copy(alpha = 0.14f) else colors.panel2
    val border = if (primary) colors.amber else colors.line
    val ink = if (primary) colors.amber else colors.ink2

    // Basinca hafifce icine cokme: dokunusun karsilik verdigi hissi.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.955f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 900f),
        label = "dugme-basis",
    )

    Text(
        text = text.uppercase(),
        style = labelStyle(ink).copy(
            fontSize = 14.sp,
            fontWeight = if (primary) FontWeight.SemiBold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        ),
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .alpha(if (enabled) 1f else 0.4f)
            .background(background, PanelShape)
            .border(1.dp, border, PanelShape)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                onClick = onClick,
            )
            .padding(horizontal = 16.dp, vertical = 11.dp),
    )
}

/** Cerceveli kucuk metin dugmesi (".ghost"). */
@Composable
fun GhostButton(
    text: String,
    colors: FlightColors,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Text(
        text = text.uppercase(),
        style = labelStyle(colors.ink2).copy(fontSize = 12.sp),
        modifier = modifier
            .background(colors.panel2, PanelShape)
            .border(1.dp, colors.line, PanelShape)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** 15/25/50 gibi secenek gruplari icin segment dugmeleri. */
@Composable
fun SegmentedOptions(
    options: List<Int>,
    selected: Int,
    colors: FlightColors,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    Row(
        modifier = modifier
            .background(colors.panel2, PanelShape)
            .border(1.dp, colors.line, PanelShape)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        for (option in options) {
            val active = option == selected
            Text(
                text = option.toString(),
                style = labelStyle(if (active) colors.amber else colors.muted).copy(
                    fontFamily = BarlowCondensed,
                    fontSize = 14.sp,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier
                    .background(
                        if (active) colors.amber.copy(alpha = 0.14f) else Color.Transparent,
                        PanelShape,
                    )
                    .clickable { onSelect(option) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

/** Ilerleme noktalari: tamamlanan bacaklar dolu, siradaki vurgulu. */
@Composable
fun Tick(
    state: TickState,
    colors: FlightColors,
    modifier: Modifier = Modifier,
) {
    val color = when (state) {
        TickState.DONE -> colors.amber
        TickState.NOW -> colors.amber2
        TickState.TODO -> colors.line
    }
    Column(
        modifier = modifier
            .size(width = 14.dp, height = 6.dp)
            .background(color, RoundedCornerShape(1.dp)),
    ) {}
}

enum class TickState { DONE, NOW, TODO }
