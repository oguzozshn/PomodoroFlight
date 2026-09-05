package com.oguzozsahin.ucusmodu.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Bir metin sutununun okunabilir kaldigi en fazla genislik.
 *
 * Tablette her sey `fillMaxWidth()` ile ekrani bastan basa geciyordu: satirlar
 * uzuyor, kure devlesiyor, geri sayima ulasmak icin kaydirmak gerekiyordu.
 * Icerik bu genislikte ortalanınca telefon hic etkilenmiyor (zaten dar), tablet
 * ise ortada duzgun bir sutun oluyor.
 */
val ContentMaxWidth = 560.dp

/** Bu genislikten sonra iki panelli duzene gecilir: solda kure, sagda kumanda. */
val TwoPaneMinWidth = 720.dp

/** Kure bir harita; metinden daha genis olabilir ama sonsuz degil. */
val GlobeMaxWidth = 900.dp

/** Iki panelli duzende panellerin arasi. */
val PaneGap = 12.dp

/** Ortalanmis, okunabilir genislikte, kaydirilabilir sutun. */
@Composable
fun ReadableColumn(
    background: Color,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    horizontalPadding: Dp = 14.dp,
    bottomPadding: Dp = 24.dp,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    maxWidth: Dp = ContentMaxWidth,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = maxWidth)
                .verticalScroll(scrollState)
                .padding(horizontal = horizontalPadding)
                .padding(bottom = bottomPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}
