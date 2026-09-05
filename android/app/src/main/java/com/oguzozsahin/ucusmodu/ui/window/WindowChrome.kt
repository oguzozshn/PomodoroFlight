package com.oguzozsahin.ucusmodu.ui.window

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp

/** Kabin ici: pencerenin cevresini saran govde rengi. */
private val CABIN = Color(0xFF20262E)
private val CABIN_EDGE = Color(0xFF39424D)

/**
 * Pencerenin onunde duran her sey: kanat ve kabin cercevesi.
 *
 * Dunya calkantiyla birlikte sallanir, bunlar sallanmaz — ikisi de ucaga
 * bagli oldugu icin sabit durmalari dogru olani.
 */
@Composable
fun WindowChrome(
    tint: Color,
    showWing: Boolean,
    showFrame: Boolean,
    /** Gece isiklarinin yanip sonmesi icin akan saniye. */
    time: Float,
    night: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        if (showWing) drawWing(tint, time, night)
        if (showFrame) drawFrame() else drawVignette()
    }
}

/** Kanat: kokte genis, uca dogru daralan, ucunda kucuk bir winglet. */
private fun DrawScope.drawWing(tint: Color, time: Float, night: Float) {
    val w = size.width
    val h = size.height

    fun p(x: Float, y: Float) = Offset(w * x, h * y)

    val top = lerp(Color(0xFFF2F4F7), tint, 0.42f)
    val shade = lerp(Color(0xFFB9C2CC), tint, 0.55f)

    val wing = Path().apply {
        moveTo(w * 1.08f, h * 0.742f)
        cubicTo(w * 0.74f, h * 0.652f, w * 0.42f, h * 0.578f, w * 0.207f, h * 0.548f)
        lineTo(w * 0.166f, h * 0.558f)
        lineTo(w * 0.232f, h * 0.596f)
        cubicTo(w * 0.47f, h * 0.672f, w * 0.78f, h * 0.812f, w * 1.08f, h * 1.005f)
        close()
    }
    drawPath(
        path = wing,
        brush = Brush.linearGradient(
            colors = listOf(top, shade),
            start = p(0.18f, 0.55f),
            end = p(1.0f, 1.0f),
        ),
    )

    // winglet
    val winglet = Path().apply {
        moveTo(w * 0.207f, h * 0.548f)
        lineTo(w * 0.170f, h * 0.462f)
        lineTo(w * 0.132f, h * 0.468f)
        lineTo(w * 0.166f, h * 0.558f)
        close()
    }
    drawPath(winglet, lerp(top, shade, 0.42f))

    // panel cizgileri: kanadin uzunlugunu hissettirir
    for (i in 0..2) {
        val k = i / 2f
        drawLine(
            color = shade.copy(alpha = 0.45f),
            start = p(0.24f + k * 0.02f, 0.572f + k * 0.008f),
            end = p(1.06f, 0.790f + k * 0.075f),
            strokeWidth = size.minDimension * 0.003f,
        )
    }

    // kanadin on kenari boyunca ince parlama
    drawLine(
        color = Color.White.copy(alpha = 0.32f),
        start = p(1.07f, 0.745f),
        end = p(0.212f, 0.549f),
        strokeWidth = size.minDimension * 0.0035f,
    )

    // sol kanat ucu isigi kirmizidir; ustune beyaz cakar isik gelir
    if (night > 0.15f) {
        val tip = p(0.148f, 0.470f)
        drawCircle(
            color = Color(0xFFFF3B30).copy(alpha = 0.20f * night),
            radius = size.minDimension * 0.045f,
            center = tip,
        )
        drawCircle(
            color = Color(0xFFFF6155).copy(alpha = 0.95f * night),
            radius = size.minDimension * 0.0075f,
            center = tip,
        )
        // yaklasik 1.4 saniyede bir kisa cakis
        val phase = (time % 1.4f)
        if (phase < 0.09f) {
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = size.minDimension * 0.018f,
                center = tip,
            )
        }
    }
}

/** Klasik yuvarlak kose kabin penceresi ve cami. */
private fun DrawScope.drawFrame() {
    val inset = Rect(
        left = size.width * 0.055f,
        top = size.height * 0.115f,
        right = size.width * 0.945f,
        bottom = size.height * 0.885f,
    )
    val radius = CornerRadius(inset.width * 0.44f, inset.height * 0.28f)
    val opening = Path().apply { addRoundRect(RoundRect(inset, radius)) }

    val surround = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(Rect(Offset.Zero, size))
        addRoundRect(RoundRect(inset, radius))
    }
    drawPath(surround, CABIN)
    drawPath(opening, color = CABIN_EDGE, style = Stroke(width = size.minDimension * 0.012f))

    clipPath(opening) {
        // camin ic golgesi
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Transparent, Color(0x66000000)),
                center = inset.center,
                radius = inset.minDimension * 0.85f,
            ),
        )
        // camdan gelen egik yansima
        val glare = Path().apply {
            moveTo(inset.left, inset.top + inset.height * 0.42f)
            lineTo(inset.left + inset.width * 0.52f, inset.top)
            lineTo(inset.left + inset.width * 0.74f, inset.top)
            lineTo(inset.left, inset.top + inset.height * 0.66f)
            close()
        }
        drawPath(glare, Color.White.copy(alpha = 0.055f))
    }

    // asagidaki gunesligin kulpu
    drawRoundRect(
        color = CABIN_EDGE,
        topLeft = Offset(size.width * 0.42f, size.height * 0.905f),
        size = Size(size.width * 0.16f, size.height * 0.012f),
        cornerRadius = CornerRadius(size.height * 0.006f),
    )
}

/** Cerceve kapaliyken kalan tek sey: kenarlara dogru hafif karartma. */
private fun DrawScope.drawVignette() {
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0x00000000), Color(0x55000000)),
            center = Offset(size.width / 2f, size.height * 0.55f),
            radius = size.minDimension * 0.95f,
        ),
    )
}

/** Kanadin renk tonu: gokyuzunun rengiyle yikanmis beyaz. */
fun wingTint(palette: SkyPalette): Color = lerp(palette.horizon, palette.cloudLit, 0.45f)
