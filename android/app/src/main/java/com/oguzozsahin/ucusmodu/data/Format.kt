package com.oguzozsahin.ucusmodu.data

import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToLong

private val TR = Locale("tr", "TR")
private val integerFormat: NumberFormat = NumberFormat.getIntegerInstance(TR)

/** ms -> "MM:SS" */
fun formatClock(ms: Long): String {
    val total = ceil(ms.coerceAtLeast(0L) / 1000.0).toLong()
    val m = total / 60
    val s = total % 60
    return "%02d:%02d".format(m, s)
}

/** Binlik ayraci Turkce olan tam sayi gosterimi. */
fun formatNumber(value: Double): String = integerFormat.format(value.roundToLong())

/** Toplam odak suresi: "12,5 sa" */
fun formatHours(ms: Long): String =
    String.format(TR, "%.1f sa", ms / 3_600_000.0)

fun formatDate(time: Long): String =
    java.text.SimpleDateFormat("dd.MM.yyyy", TR).format(java.util.Date(time))

/** Gun icindeki saat: "03:25" (ertesi gune tasiyorsa yaninda gun farki). */
fun formatTimeOfDay(time: Long, now: Long = System.currentTimeMillis()): String {
    val clock = java.text.SimpleDateFormat("HH:mm", TR).format(java.util.Date(time))
    val days = ((time / 86_400_000L) - (now / 86_400_000L)).toInt()
    return if (days > 0) "$clock (+$days gün)" else clock
}

/**
 * Turkce buyuk harf.
 *
 * Kotlin'in `uppercase()` fonksiyonu yerelden bagimsiz calisir ve `i` harfini
 * `I` yapar. Turkcede karsiligi `İ`; aksi halde ekranda "BILET KES",
 * "HER ŞEYI SIL", "KAT EDILEN YOL" yaziyor.
 */
fun String.upperTr(): String = uppercase(TR)
