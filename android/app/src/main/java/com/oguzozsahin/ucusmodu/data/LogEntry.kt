package com.oguzozsahin.ucusmodu.data

/**
 * Biten bir ucusun gunluk satiri.
 *
 * @param km rotanin toplam uzunlugu; donusumden gelen satirda bilinmiyorsa 0
 * @param flown uculan km
 * @param start ucusun basladigi an (bilet)
 * @param done varildi mi. Eski `{r, km, t}` satirlari yalniz varista
 *   yaziliyordu, o yuzden varsayilan tamamlanmis.
 */
data class LogEntry(
    val routeKey: String,
    val km: Int,
    val time: Long,
    val flown: Double = km.toDouble(),
    val start: Long = time,
    val done: Boolean = true,
)
