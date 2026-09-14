package com.oguzozsahin.ucusmodu.data

/** Ucusa baslamadan once kesilen binis karti. */
data class Ticket(
    val route: Route,
    val passenger: String,
    val flightNo: String,
    val gate: String,
    val seat: String,
    val boardingAt: Long,
    val departureAt: Long,
    val arrivalAt: Long,
    val distanceKm: Double,
    val legs: Int,
    val focusMinutes: Int,
) {
    /** Barkodun cizgi genislikleri; ayni ucus icin hep ayni desen. */
    val barcode: IntArray get() = barcodePattern("$flightNo$seat$gate")
}

private const val SEAT_ROWS = 38
private const val SEAT_LETTERS = "ABCDEF"

/** Rotaya ve gune sabitlenmis koltuk: ayni gun ayni rota ayni koltugu verir. */
fun seatOf(routeKey: String, issuedAt: Long): String {
    val day = issuedAt / 86_400_000L
    var hash = day.toInt()
    for (ch in routeKey) hash = (hash * 31 + ch.code) and 0x7FFFFFFF
    val row = 1 + hash % SEAT_ROWS
    val letter = SEAT_LETTERS[(hash / SEAT_ROWS) % SEAT_LETTERS.length]
    return "$row$letter"
}

/**
 * Dekoratif barkod deseni: tohumdan turetilen, 1-3 birim arasi degisen
 * cizgi genislikleri. Gercek bir barkod degildir, biletin gorunumu icindir.
 */
fun barcodePattern(seed: String, count: Int = 48): IntArray {
    var state = 0x9E3779B9.toInt()
    for (ch in seed) state = state * 31 + ch.code
    return IntArray(count) {
        state = state * 1_103_515_245 + 12_345
        1 + ((state ushr 16) and 0x7FFF) % 3
    }
}

/** Yolcu adi bos birakilmissa biletin uzerinde "YOLCU" yazar. */
fun passengerOrDefault(name: String): String =
    name.trim().ifEmpty { "YOLCU" }.uppercase(java.util.Locale("tr", "TR"))

/** Secili rota ve ayarlardan bir bilet keser. */
fun issueTicket(state: FlightUiState, now: Long = System.currentTimeMillis()): Ticket {
    val scheduleMinutes = remainingScheduleMinutes(
        legsLeft = state.legsLeft,
        focusMin = state.focusMin,
        breakMin = state.breakMin,
        cycle = state.cycle,
        longBreakMin = state.longBreakMin,
    )
    return Ticket(
        route = state.route,
        passenger = passengerOrDefault(state.passenger),
        flightNo = state.route.flightNo,
        gate = gateOf(state.route.key),
        seat = seatOf(state.route.key, now),
        boardingAt = now,
        departureAt = now + BOARDING_MINUTES * 60_000L,
        arrivalAt = now + (BOARDING_MINUTES + scheduleMinutes) * 60_000L,
        distanceKm = state.route.km,
        legs = state.legsLeft,
        focusMinutes = focusMinutesFor(state.legsLeft),
    )
}

/** Binis ile kalkis arasindaki sure. */
const val BOARDING_MINUTES = 15
