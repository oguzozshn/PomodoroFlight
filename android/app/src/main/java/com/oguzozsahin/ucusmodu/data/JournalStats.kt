package com.oguzozsahin.ucusmodu.data

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Bir gunde tamamlanan pomodoro sayisi; oturumlar 25 dakikanin kati olmayabilir. */
data class DayCount(val date: LocalDate, val count: Double)

/**
 * Gunluk kayitlarindan cikan ozetler.
 *
 * Tamami saf: ne Android'e ne dosyaya bagli, o yuzden birim testlerde
 * dogrudan olculebiliyor. Gun siniri cihazin yerel saatine gore cizilir;
 * zaman dilimi disaridan verilebildigi icin test de belirli kaliyor.
 *
 * Sayimlar satir degil pomodoro cinsinden: bir satir bir odak oturumu, ama
 * pomodoro 25 dakika. 50 dakikalik oturum iki, 37 dakikalik 1,48 sayilir;
 * tamamlanmis 25 dakikalik eski kayitlar tam bir kaliyor.
 */
object JournalStats {

    /** Atlanan pomodorolar odak istatistiklerine katilmaz. */
    fun counted(records: List<FocusRecord>): List<FocusRecord> = records.filter { !it.skipped }

    /** Bir kaydin kac pomodoro ettigi. */
    fun pomodorosOf(record: FocusRecord): Double = record.minutes / POMODORO_MIN.toDouble()

    fun dateOf(time: Long, zone: ZoneId): LocalDate =
        Instant.ofEpochMilli(time).atZone(zone).toLocalDate()

    /** Bugun dahil son [days] gunun sayimi; liste eskiden yeniye siralidir. */
    fun dailyCounts(
        records: List<FocusRecord>,
        days: Int,
        today: LocalDate,
        zone: ZoneId,
    ): List<DayCount> {
        val buckets = bucketByDay(records, zone)
        return List(days) { i ->
            val date = today.minusDays((days - 1 - i).toLong())
            DayCount(date, buckets[date] ?: 0.0)
        }
    }

    private fun bucketByDay(records: List<FocusRecord>, zone: ZoneId): Map<LocalDate, Double> {
        val buckets = HashMap<LocalDate, Double>()
        for (record in counted(records)) {
            val date = dateOf(record.time, zone)
            buckets[date] = (buckets[date] ?: 0.0) + pomodorosOf(record)
        }
        return buckets
    }

    /**
     * Ust uste kac gun. Bugun bos olabilir — dun calisilmissa seri bozulmamis
     * sayilir, gun bitmedigi icin.
     */
    fun streak(records: List<FocusRecord>, today: LocalDate, zone: ZoneId): Int {
        val days = counted(records).mapTo(HashSet()) { dateOf(it.time, zone) }
        if (days.isEmpty()) return 0
        var cursor = if (days.contains(today)) today else today.minusDays(1)
        if (!days.contains(cursor)) return 0
        var length = 0
        while (days.contains(cursor)) {
            length++
            cursor = cursor.minusDays(1)
        }
        return length
    }

    /** Gunun hangi saatlerinde calisildigi; 24 kutu, pomodoro cinsinden. */
    fun hourHistogram(records: List<FocusRecord>, zone: ZoneId): DoubleArray {
        val hours = DoubleArray(24)
        for (record in counted(records)) {
            val hour = Instant.ofEpochMilli(record.time).atZone(zone).hour
            hours[hour] += pomodorosOf(record)
        }
        return hours
    }

    /** En yogun ardisik iki saat; histogramda vurgulanan aralik. */
    fun peakWindow(hours: DoubleArray): Int? {
        if (hours.sum() <= 0.0) return null
        var best = 0
        var bestTotal = -1.0
        for (h in 0 until 24) {
            val total = hours[h] + hours[(h + 1) % 24]
            if (total > bestTotal) {
                bestTotal = total
                best = h
            }
        }
        return best
    }

    fun topRoutes(records: List<FocusRecord>, limit: Int): List<Pair<String, Double>> {
        val counts = HashMap<String, Double>()
        for (record in counted(records)) {
            counts[record.routeKey] = (counts[record.routeKey] ?: 0.0) + pomodorosOf(record)
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .take(limit)
            .map { it.key to it.value }
    }

    fun busiestDay(records: List<FocusRecord>, zone: ZoneId): DayCount? =
        bucketByDay(records, zone).entries
            .sortedWith(compareByDescending<Map.Entry<LocalDate, Double>> { it.value }.thenBy { it.key })
            .firstOrNull()
            ?.let { DayCount(it.key, it.value) }

    /** Hic olmazsa bir pomodoro yapilmis gun sayisi. */
    fun activeDays(records: List<FocusRecord>, zone: ZoneId): Int =
        counted(records).mapTo(HashSet()) { dateOf(it.time, zone) }.size

    fun focusMinutes(records: List<FocusRecord>): Int = counted(records).sumOf { it.minutes }

    /** Ugranilan havalimani kodlari; rotanin iki ucu da sayilir. */
    fun airports(records: List<FocusRecord>): Set<String> {
        val codes = HashSet<String>()
        for (record in counted(records)) codes.addAll(record.endpoints)
        return codes
    }
}
