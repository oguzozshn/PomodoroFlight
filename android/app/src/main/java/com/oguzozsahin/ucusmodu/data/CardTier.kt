package com.oguzozsahin.ucusmodu.data

/**
 * Havayolu kart seviyesi. Esikler dunya turuna baglanir: bir tur Silver,
 * bes tur Elite, on iki buçuk tur Elite Plus.
 */
enum class CardTier(val title: String, val minKm: Double) {
    CLASSIC("Classic", 0.0),
    SILVER("Silver", EARTH_CIRCUMFERENCE_KM),
    ELITE("Elite", EARTH_CIRCUMFERENCE_KM * 5),
    ELITE_PLUS("Elite Plus", EARTH_CIRCUMFERENCE_KM * 12.5),
}

fun tierOf(km: Double): CardTier {
    var current = CardTier.CLASSIC
    for (tier in CardTier.entries) if (km >= tier.minKm) current = tier
    return current
}

fun nextTierOf(km: Double): CardTier? {
    val current = tierOf(km)
    val index = current.ordinal + 1
    return if (index < CardTier.entries.size) CardTier.entries[index] else null
}

/** Bulunulan seviyeden bir sonrakine ilerleme (0..1); en ustte 1. */
fun tierProgress(km: Double): Float {
    val current = tierOf(km)
    val next = nextTierOf(km) ?: return 1f
    val span = next.minKm - current.minKm
    if (span <= 0.0) return 1f
    return ((km - current.minKm) / span).coerceIn(0.0, 1.0).toFloat()
}

/** Bir sonraki seviyeye kalan mesafe; en ustte 0. */
fun kmToNextTier(km: Double): Double {
    val next = nextTierOf(km) ?: return 0.0
    return (next.minKm - km).coerceAtLeast(0.0)
}
