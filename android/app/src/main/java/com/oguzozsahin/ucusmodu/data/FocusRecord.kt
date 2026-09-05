package com.oguzozsahin.ucusmodu.data

/**
 * Tamamlanmis tek bir odak pomodorosu.
 *
 * Sayaclarin (toplam km, toplam sure) aksine burada zaman damgasi var; gunluk
 * dagilim, seri ve saat histogrami ancak boyle hesaplanabiliyor.
 */
data class FocusRecord(
    /** Pomodoronun bittigi an. */
    val time: Long,
    val routeKey: String,
    val minutes: Int,
    val km: Double,
    /** ATLA ile erken gecildiyse dogru; odak istatistiklerine katilmaz. */
    val skipped: Boolean,
) {
    /** Rotanin iki ucundaki havalimani kodlari. */
    val endpoints: List<String> get() = routeKey.split("-").filter { it.isNotBlank() }
}

/**
 * Gunlugun satir bicimi: sekmeyle ayrilmis duz metin.
 *
 * Depodaki UMC1/UMP2 ikili bicimlerinin aksine burada okunabilirlik boyuttan
 * onemli: bu kullanicinin kendi verisi ve `adb shell cat` ile bakilabilmesi
 * ise yariyor. Satir basina ~35 bayt, gunde 20 pomodoro icin yilda ~250 KB.
 */
object JournalFormat {

    const val HEADER = "# ucus-gunlugu v1: zaman\trota\tdakika\tkm\tatlandi"

    fun encode(record: FocusRecord): String = buildString {
        append(record.time)
        append('\t')
        append(record.routeKey)
        append('\t')
        append(record.minutes)
        append('\t')
        append(formatKm(record.km))
        append('\t')
        append(if (record.skipped) '1' else '0')
    }

    /** Bozuk ya da yarim yazilmis satirlar sessizce elenir, null doner. */
    fun decode(line: String): FocusRecord? {
        val trimmed = line.trim()
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return null
        val parts = trimmed.split('\t')
        if (parts.size < 5) return null
        val time = parts[0].toLongOrNull() ?: return null
        if (time <= 0L) return null
        val route = parts[1]
        if (route.isBlank()) return null
        val minutes = parts[2].toIntOrNull() ?: return null
        val km = parts[3].toDoubleOrNull() ?: return null
        if (minutes < 0 || km < 0.0) return null
        return FocusRecord(
            time = time,
            routeKey = route,
            minutes = minutes,
            km = km,
            skipped = parts[4] == "1",
        )
    }

    /** Yereldan bagimsiz nokta ayracli sayi; dosya her cihazda ayni okunmali. */
    private fun formatKm(km: Double): String =
        String.format(java.util.Locale.ROOT, "%.1f", km)
}
