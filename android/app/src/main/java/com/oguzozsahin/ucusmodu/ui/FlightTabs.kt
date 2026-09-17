package com.oguzozsahin.ucusmodu.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.R
import com.oguzozsahin.ucusmodu.data.upperTr
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.labelStyle

/** Uygulamanin sekmeleri: ucus akisi, ucus gunlugu ve ayarlar. */
enum class FlightTab(val label: String, @DrawableRes val icon: Int) {
    FLIGHT("Uçuş", R.drawable.ic_plane),
    LOGBOOK("Uçuş günlüğü", R.drawable.ic_logbook),
    SETTINGS("Ayarlar", R.drawable.ic_settings),
}

/** Telefonda alt gezinme cubugu. Sistem boşluklari kokte uygulandigi icin burada eklenmez. */
@Composable
fun FlightTabBar(selected: FlightTab, colors: FlightColors, onSelect: (FlightTab) -> Unit) {
    Column {
        HorizontalDivider(color = colors.line)
        NavigationBar(
            containerColor = colors.panel,
            tonalElevation = 0.dp,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            for (tab in FlightTab.entries) {
                val active = tab == selected
                NavigationBarItem(
                    selected = active,
                    onClick = { onSelect(tab) },
                    icon = { TabIcon(tab) },
                    label = { TabLabel(tab, active, colors) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.amber,
                        unselectedIconColor = colors.muted,
                        indicatorColor = colors.panel2,
                    ),
                )
            }
        }
    }
}

/** Genis ekranda yan ray. */
@Composable
fun FlightTabRail(selected: FlightTab, colors: FlightColors, onSelect: (FlightTab) -> Unit) {
    Row(Modifier.fillMaxHeight()) {
        NavigationRail(
            containerColor = colors.panel,
            windowInsets = WindowInsets(0, 0, 0, 0),
        ) {
            Spacer(Modifier.height(12.dp))
            for (tab in FlightTab.entries) {
                val active = tab == selected
                NavigationRailItem(
                    selected = active,
                    onClick = { onSelect(tab) },
                    icon = { TabIcon(tab) },
                    label = { TabLabel(tab, active, colors) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = colors.amber,
                        unselectedIconColor = colors.muted,
                        indicatorColor = colors.panel2,
                    ),
                )
            }
        }
        VerticalDivider(color = colors.line)
    }
}

@Composable
private fun TabIcon(tab: FlightTab) {
    Icon(painterResource(tab.icon), contentDescription = null, modifier = Modifier.size(22.dp))
}

@Composable
private fun TabLabel(tab: FlightTab, active: Boolean, colors: FlightColors) {
    Text(
        tab.label.upperTr(),
        style = labelStyle(if (active) colors.amber else colors.muted).copy(fontSize = 11.sp),
        textAlign = TextAlign.Center,
    )
}
