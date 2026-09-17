package com.oguzozsahin.ucusmodu.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.oguzozsahin.ucusmodu.data.Airports
import com.oguzozsahin.ucusmodu.data.Flights
import com.oguzozsahin.ucusmodu.data.LogEntry
import com.oguzozsahin.ucusmodu.data.formatDate
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.monoStyle

/**
 * Ucus gunlugunun tek satiri. Ucus ekranindaki kisa liste ile mil
 * bildirimindeki tam liste ayni bicimi kullaniyor; iki yerde ayri yazilmasin.
 *
 * Yarida birakilan ucus soluk renkte ve ne kadarinin uculdugunu soyler.
 */
@Composable
fun FlightLogRow(entry: LogEntry, colors: FlightColors) {
    val parts = entry.routeKey.split("-")
    val destCity = Airports[parts.getOrNull(1) ?: ""]?.city ?: parts.getOrNull(1).orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.routeKey.replace("-", "→"),
            style = monoStyle(if (entry.done) colors.amber else colors.muted, 12),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(destCity, style = monoStyle(if (entry.done) colors.ink2 else colors.muted, 12))
            Text(
                Flights.describe(entry),
                style = monoStyle(if (entry.done) colors.ink2 else colors.muted, 10),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(formatDate(entry.time), style = monoStyle(colors.muted, 11))
    }
    HorizontalDivider(color = colors.lineSoft)
}
