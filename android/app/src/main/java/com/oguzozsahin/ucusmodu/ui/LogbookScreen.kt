package com.oguzozsahin.ucusmodu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oguzozsahin.ucusmodu.data.Airports
import com.oguzozsahin.ucusmodu.data.CardTier
import com.oguzozsahin.ucusmodu.data.DayCount
import com.oguzozsahin.ucusmodu.data.EARTH_CIRCUMFERENCE_KM
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.FocusRecord
import com.oguzozsahin.ucusmodu.data.Journal
import com.oguzozsahin.ucusmodu.data.JournalStats
import com.oguzozsahin.ucusmodu.data.currentLapFraction
import com.oguzozsahin.ucusmodu.data.formatDate
import com.oguzozsahin.ucusmodu.data.formatDuration
import com.oguzozsahin.ucusmodu.data.formatNumber
import com.oguzozsahin.ucusmodu.data.kmToNextTier
import com.oguzozsahin.ucusmodu.data.nextTierOf
import com.oguzozsahin.ucusmodu.data.tierOf
import com.oguzozsahin.ucusmodu.data.tierProgress
import com.oguzozsahin.ucusmodu.data.upperTr
import com.oguzozsahin.ucusmodu.data.worldLaps
import com.oguzozsahin.ucusmodu.ui.theme.FlightColors
import com.oguzozsahin.ucusmodu.ui.theme.LocalFlightColors
import com.oguzozsahin.ucusmodu.ui.theme.labelStyle
import com.oguzozsahin.ucusmodu.ui.theme.monoStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlin.math.floor

/** Isi haritasinda kac hafta gosterilir. */
private const val HEATMAP_WEEKS = 12

private val TR = Locale("tr", "TR")

/**
 * Mil bildirimi: uygulamanin biriktirdigi her seyin tek yerde ozeti.
 *
 * Tasarimi bilerek pomodoro uygulamalarinin cubuk grafigine benzemiyor; bu
 * uygulamada dakikalar zaten mesafeye ve rotaya cevrildigi icin havayolu
 * bildirimi daha dogru bir cerceve.
 */
@Composable
fun LogbookScreen(
    state: FlightUiState,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalFlightColors.current
    val context = LocalContext.current
    val zone = remember { ZoneId.systemDefault() }

    var records by remember { mutableStateOf<List<FocusRecord>?>(null) }
    LaunchedEffect(state.totalKm, state.log.size) {
        val journal = Journal(context)
        records = withContext(Dispatchers.IO) { journal.read() }
    }

    val loaded = records
    val today = remember(loaded) { LocalDate.now(zone) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.ground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Cikis her ekranda ayni yerde: sol ust.
            GhostButton("← Geri", colors, onClick = onBack)
            Spacer(Modifier.width(10.dp))
            Text("MİL BİLDİRİMİ", style = labelStyle(colors.ink2).copy(fontSize = 13.sp))
        }

        MembershipCard(state, colors)
        WorldTourPanel(state.totalKm, colors)
        FlightMapPanel(state, colors)

        if (loaded == null) {
            Panel(colors) {
                Text(
                    "Günlük okunuyor…",
                    style = monoStyle(colors.muted, 12),
                    modifier = Modifier.padding(14.dp),
                )
            }
        } else {
            NumbersPanel(state, loaded, today, zone, colors)
            CalendarPanel(loaded, today, zone, colors, recordingNote(loaded, zone))
            HoursPanel(loaded, zone, colors)
            TopRoutesPanel(loaded, colors)
        }

        StampsPanel(state, colors)

        Text(
            text = "Toplam mil ve uçuş günlüğü baştan beri tutuluyor; " +
                "gün bazlı görünümler kayıt başladığından beri.",
            style = monoStyle(colors.muted, 10),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
    }
}

/** Kart seviyesi ve toplam mil: bildirimin kapagi. */
@Composable
private fun MembershipCard(state: FlightUiState, colors: FlightColors) {
    val tier = tierOf(state.totalKm)
    val next = nextTierOf(state.totalKm)
    val progress = tierProgress(state.totalKm)

    Panel(colors) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("UÇUŞ KARTI", style = labelStyle(colors.muted).copy(fontSize = 10.sp))
                Spacer(Modifier.weight(1f))
                Text(
                    tier.title.upperTr(),
                    style = labelStyle(colors.amber).copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = state.passenger.ifBlank { "YOLCU" }.upperTr(),
                style = monoStyle(colors.ink2, 13),
            )
            Spacer(Modifier.height(14.dp))
            Text("TOPLAM MİL", style = labelStyle(colors.muted).copy(fontSize = 10.sp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(formatNumber(state.totalKm), style = monoStyle(colors.ink, 34, FontWeight.Light))
                Text(
                    "km",
                    style = monoStyle(colors.muted, 13),
                    modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                )
            }

            if (next != null) {
                Spacer(Modifier.height(12.dp))
                Rail(progress, colors)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "${next.title} için ${formatNumber(kmToNextTier(state.totalKm))} km kaldı",
                    style = monoStyle(colors.muted, 11),
                )
            } else {
                Spacer(Modifier.height(12.dp))
                Text(
                    "En üst seviyedesin — buradan sonrası saf keyif.",
                    style = monoStyle(colors.muted, 11),
                )
            }
        }
    }
}

/** Dunya turu: toplam yolun kac tur ettigi. */
@Composable
private fun WorldTourPanel(totalKm: Double, colors: FlightColors) {
    val laps = worldLaps(totalKm)
    val fraction = currentLapFraction(totalKm)
    val completed = floor(laps).toInt()

    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DÜNYA TURU", style = labelStyle(colors.muted).copy(fontSize = 10.sp))
                Spacer(Modifier.weight(1f))
                Text(
                    if (completed > 0) "$completed tur tamam" else "ilk tur sürüyor",
                    style = monoStyle(colors.ink2, 11),
                )
            }
            Spacer(Modifier.height(10.dp))
            Rail(fraction, colors)
            Spacer(Modifier.height(6.dp))
            Text(
                "${formatNumber(totalKm % EARTH_CIRCUMFERENCE_KM)} / ${formatNumber(EARTH_CIRCUMFERENCE_KM)} km",
                style = monoStyle(colors.muted, 11),
            )
        }
    }
}

/** Ucusun izleri: tamamlanan bütün rotalar kurede. */
@Composable
private fun FlightMapPanel(state: FlightUiState, colors: FlightColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(colors.space, PanelShape)
            .border(1.dp, colors.line, PanelShape),
    ) {
        GlobeMap(
            route = state.route,
            fraction = state.fraction,
            colors = colors,
            trails = state.log.map { it.routeKey },
            modifier = Modifier.fillMaxSize(),
        )
        Text(
            "UÇUŞ HARİTASI",
            style = labelStyle(colors.muted).copy(fontSize = 10.sp),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp),
        )
    }
}

/** Sayilar: her biri tek basina bir cumle, grafige gerek yok. */
@Composable
private fun NumbersPanel(
    state: FlightUiState,
    records: List<FocusRecord>,
    today: LocalDate,
    zone: ZoneId,
    colors: FlightColors,
) {
    val streak = JournalStats.streak(records, today, zone)
    val active = JournalStats.activeDays(records, zone)
    val busiest = JournalStats.busiestDay(records, zone)
    val airports = JournalStats.airports(records).size
    val minutes = JournalStats.focusMinutes(records)

    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            Text("RAKAMLAR", style = labelStyle(colors.muted).copy(fontSize = 10.sp))
            Spacer(Modifier.height(12.dp))
            Row {
                Figure("Seri", if (streak > 0) "$streak gün" else "—", colors, Modifier.weight(1f))
                Figure("Aktif gün", "$active", colors, Modifier.weight(1f))
                Figure("Kayıtlı odak", formatDuration(minutes), colors, Modifier.weight(1f))
            }
            Spacer(Modifier.height(14.dp))
            Row {
                Figure("Tamamlanan uçuş", "${state.log.size}", colors, Modifier.weight(1f))
                Figure("Uğranan yer", if (airports > 0) "$airports" else "—", colors, Modifier.weight(1f))
                Figure(
                    "En yoğun gün",
                    busiest?.let { "${it.count} pom." } ?: "—",
                    colors,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Ucus takvimi: son 12 haftanin gunluk yogunlugu.
 *
 * Tek renkli merdiven (bos -> yogun); buyukluk zaten renkle anlatildigi icin
 * her hucreye sayi yazilmiyor, dokunulan gunun sayisi altta okunuyor.
 */
@Composable
private fun CalendarPanel(
    records: List<FocusRecord>,
    today: LocalDate,
    zone: ZoneId,
    colors: FlightColors,
    /** Izgara bosken hemen altinda gorunecek aciklama. */
    emptyNote: String,
) {
    // Izgara pazartesiyle baslasin diye bugunun haftasinin sonuna kadar doldurulur.
    val trailing = (7 - today.dayOfWeek.value) // pazartesi=1 ... pazar=7
    val days = HEATMAP_WEEKS * 7
    val gridEnd = today.plusDays(trailing.toLong())
    val counts = JournalStats.dailyCounts(records, days, gridEnd, zone)
    val peak = counts.maxOfOrNull { it.count } ?: 0

    var selected by remember { mutableStateOf<DayCount?>(null) }

    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("UÇUŞ TAKVİMİ", style = labelStyle(colors.muted).copy(fontSize = 10.sp))
                Spacer(Modifier.weight(1f))
                Text("son 12 hafta", style = monoStyle(colors.muted, 10))
            }
            Spacer(Modifier.height(10.dp))

            // 7 satir (gunler) x 12 sutun (haftalar)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                for (weekday in 0 until 7) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        for (week in 0 until HEATMAP_WEEKS) {
                            val index = week * 7 + weekday
                            val day = counts.getOrNull(index)
                            val future = day != null && day.date.isAfter(today)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .background(
                                        color = if (day == null || future) {
                                            Color.Transparent
                                        } else {
                                            heatColor(day.count, peak, colors)
                                        },
                                        shape = CellShape,
                                    )
                                    .clickable(enabled = day != null && !future) {
                                        selected = if (selected?.date == day?.date) null else day
                                    },
                            )
                        }
                    }
                }
            }

            if (peak == 0) {
                // Bos izgaranin aciklamasi sayfanin dibinde duruyordu; orada
                // kimse okumuyor ve ekran bozuk gorunuyordu.
                Spacer(Modifier.height(10.dp))
                Text(emptyNote, style = monoStyle(colors.muted, 10))
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val chosen = selected
                Text(
                    text = if (chosen == null) {
                        "Bir güne dokun"
                    } else {
                        val millis = chosen.date.atStartOfDay(zone).toInstant().toEpochMilli()
                        "${formatDate(millis)} · ${chosen.count} pomodoro"
                    },
                    style = monoStyle(if (chosen == null) colors.muted else colors.ink2, 11),
                    modifier = Modifier.weight(1f),
                )
                Text("az", style = monoStyle(colors.muted, 10))
                Spacer(Modifier.width(4.dp))
                for (step in colors.heat.drop(1)) {
                    Box(
                        modifier = Modifier
                            .padding(end = 3.dp)
                            .size(10.dp)
                            .background(step, CellShape),
                    )
                }
                Text("çok", style = monoStyle(colors.muted, 10))
            }
        }
    }
}

/**
 * Saat dagilimi. Tek dizi oldugu icin butun cubuklar ayni renkte; uzunluk
 * zaten buyuklugu anlatiyor, renk ikinci kez ayni seyi soylemiyor. Yalnizca
 * en yogun aralik vurgulaniyor.
 */
@Composable
private fun HoursPanel(records: List<FocusRecord>, zone: ZoneId, colors: FlightColors) {
    val hours = remember(records) { JournalStats.hourHistogram(records, zone) }
    val peakHour = remember(records) { JournalStats.peakWindow(hours) }
    val peak = hours.maxOrNull() ?: 0

    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SAAT DAĞILIMI", style = labelStyle(colors.muted).copy(fontSize = 10.sp))
                Spacer(Modifier.weight(1f))
                if (peakHour != null) {
                    Text(
                        "en verimli %02d:00–%02d:00".format(peakHour, (peakHour + 2) % 24),
                        style = monoStyle(colors.ink2, 10),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(74.dp),
            ) {
                val gap = 2.dp.toPx()
                val slot = size.width / 24f
                val barWidth = (slot - gap).coerceAtLeast(1f)
                val radius = CornerRadius(minOf(2.dp.toPx(), barWidth / 2f))
                val baseline = size.height - 1.dp.toPx()

                // taban: yuzeyden bir ton ayrilan ince duz cizgi
                drawRect(
                    color = colors.line,
                    topLeft = Offset(0f, baseline),
                    size = Size(size.width, 1.dp.toPx()),
                )

                for (hour in 0 until 24) {
                    val value = hours[hour]
                    val highlighted = peakHour != null && (hour == peakHour || hour == (peakHour + 1) % 24)
                    val height = if (peak == 0) 0f else baseline * (value.toFloat() / peak) * 0.94f
                    val x = hour * slot + gap / 2f
                    if (height < 0.5f) {
                        // bos saat: sifir oldugunu gosteren silik bir kok
                        drawRect(
                            color = colors.lineSoft,
                            topLeft = Offset(x, baseline - 1.5.dp.toPx()),
                            size = Size(barWidth, 1.5.dp.toPx()),
                        )
                        continue
                    }
                    drawRoundRect(
                        color = if (highlighted) colors.amber else colors.amber.copy(alpha = 0.38f),
                        topLeft = Offset(x, baseline - height),
                        size = Size(barWidth, height),
                        cornerRadius = radius,
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                for (label in listOf("00", "06", "12", "18")) {
                    Text(
                        label,
                        style = monoStyle(colors.muted, 9),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** En cok ucuulan hatlar. */
@Composable
private fun TopRoutesPanel(records: List<FocusRecord>, colors: FlightColors) {
    val top = remember(records) { JournalStats.topRoutes(records, 5) }
    if (top.isEmpty()) return
    val most = top.first().second

    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            Text("EN ÇOK UÇULAN HAT", style = labelStyle(colors.muted).copy(fontSize = 10.sp))
            Spacer(Modifier.height(10.dp))
            for ((routeKey, count) in top) {
                val parts = routeKey.split("-")
                val destCity = Airports[parts.getOrNull(1) ?: ""]?.city ?: parts.getOrNull(1).orEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(routeKey.replace("-", "→"), style = monoStyle(colors.amber, 12))
                    Spacer(Modifier.width(10.dp))
                    Text(destCity, style = monoStyle(colors.ink2, 12), modifier = Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .width(52.dp)
                            .height(6.dp)
                            .background(colors.panel2, CellShape),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(if (most == 0) 0f else count.toFloat() / most)
                                .height(6.dp)
                                .background(colors.amber.copy(alpha = 0.55f), CellShape),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("$count", style = monoStyle(colors.ink2, 11))
                }
            }
        }
    }
}

/** Pasaport damgalari: tamamlanan ucuslar. */
@Composable
private fun StampsPanel(state: FlightUiState, colors: FlightColors) {
    Panel(colors) {
        Column(Modifier.padding(14.dp)) {
            Text("UÇUŞ GÜNLÜĞÜ", style = labelStyle(colors.muted).copy(fontSize = 10.sp))
            Spacer(Modifier.height(10.dp))
            if (state.log.isEmpty()) {
                Text(
                    "Henüz tamamlanmış uçuş yok. İlk rotanı bitirdiğinde buraya damga düşecek.",
                    style = monoStyle(colors.muted, 11),
                )
            } else {
                for (entry in state.log.take(20)) {
                    val parts = entry.routeKey.split("-")
                    val destCity = Airports[parts.getOrNull(1) ?: ""]?.city ?: parts.getOrNull(1).orEmpty()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(entry.routeKey.replace("-", "→"), style = monoStyle(colors.amber, 12))
                        Spacer(Modifier.width(10.dp))
                        Text(destCity, style = monoStyle(colors.ink2, 12), modifier = Modifier.weight(1f))
                        Text("${formatNumber(entry.km.toDouble())} km", style = monoStyle(colors.muted, 11))
                        Spacer(Modifier.width(10.dp))
                        Text(formatDate(entry.time), style = monoStyle(colors.muted, 11))
                    }
                    HorizontalDivider(color = colors.lineSoft)
                }
            }
        }
    }
}

@Composable
private fun Figure(label: String, value: String, colors: FlightColors, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.upperTr(), style = labelStyle(colors.muted).copy(fontSize = 9.sp))
        Spacer(Modifier.height(3.dp))
        Text(value, style = monoStyle(colors.ink, 15, FontWeight.Medium))
    }
}

/** Ince ilerleme rayi; kart seviyesi ve dunya turu ayni bicimi kullanir. */
@Composable
private fun Rail(fraction: Float, colors: FlightColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(colors.panel2, CellShape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .height(4.dp)
                .background(colors.amber, CellShape),
        )
    }
}

/** Bos hucre en alt basamak; geri kalanlar yogunluga gore tirmanir. */
private fun heatColor(count: Int, peak: Int, colors: FlightColors): Color {
    if (count <= 0) return colors.heat[0]
    if (peak <= 0) return colors.heat[0]
    val steps = colors.heat.size - 1
    val level = ((count - 1) * (steps - 1) / maxOf(1, peak - 1)) + 1
    return colors.heat[level.coerceIn(1, steps)]
}

/** Kaydin ne zaman baslamis oldugunu durustce yazar. */
private fun recordingNote(records: List<FocusRecord>?, zone: ZoneId): String {
    if (records == null) return " "
    val first = records.minByOrNull { it.time }
        ?: return "Burası ilk pomodoron bittiğinde dolmaya başlar — bozuk değil, henüz boş."
    val date = JournalStats.dateOf(first.time, zone)
    val month = date.month.getDisplayName(DateTextStyle.FULL, TR)
    return "Gün bazlı kayıt ${date.dayOfMonth} $month ${date.year} tarihinde başladı, " +
        "${records.size} kayıt var."
}
