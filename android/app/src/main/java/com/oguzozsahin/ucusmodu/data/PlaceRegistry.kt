package com.oguzozsahin.ucusmodu.data

import java.util.Locale

private val TURKISH = Locale("tr", "TR")

/**
 * Aramayi aksanlardan ve Turkce buyuk/kucuk harf tuhafliklarindan bagimsiz kilar:
 * "istanbul" -> İstanbul, "diyarbakir" -> Diyarbakır.
 */
fun normalizeForSearch(text: String): String {
    val lower = text.lowercase(TURKISH)
    val out = StringBuilder(lower.length)
    for (ch in lower) {
        out.append(
            when (ch) {
                'ı', 'î', 'í', 'ì', 'ï' -> 'i'
                'ş' -> 's'
                'ğ' -> 'g'
                'ü', 'û', 'ù', 'ú' -> 'u'
                'ö', 'ô', 'ò', 'ó', 'õ' -> 'o'
                'ç' -> 'c'
                'â', 'á', 'à', 'ä', 'ã', 'å' -> 'a'
                'é', 'è', 'ê', 'ë' -> 'e'
                'ñ' -> 'n'
                else -> ch
            },
        )
    }
    return out.toString()
}

/**
 * Uygulamanin tanidigi tum yerler. Elle yazilmis Turkce adlar (Airports) veri
 * setinin uzerinde kalir; geri kalan her sey varliktan yuklenir.
 */
object PlaceRegistry {

    @Volatile
    private var loaded: List<Airport> = emptyList()

    @Volatile
    private var byCode: Map<String, Airport> = emptyMap()

    /** Her yer icin aranabilir adlar: sehir, havalimani ve varsa Turkce karsiligi. */
    @Volatile
    private var searchKeys: List<Array<String>> = emptyList()

    /** Varliktan okunan listeyi devreye alir. */
    fun install(places: List<Airport>) {
        loaded = places.map { withPreferredName(it) }
        byCode = loaded.associateBy { it.code }
        searchKeys = loaded.map { place ->
            val raw = mutableListOf(place.city)
            if (place.airportName.isNotBlank()) raw += place.airportName
            Airports[place.code]?.city?.let { raw += it }
            TurkishCities[place.code]?.let { raw += it }
            raw.map { normalizeForSearch(it) }.distinct().toTypedArray()
        }
    }

    val isLoaded: Boolean get() = loaded.isNotEmpty()

    /** Onem sirasina gore (once buyuk havalimanlari) tum yerler. */
    fun all(): List<Airport> = loaded

    /**
     * Kodu cozer. Elle girilmis Turkce ad ve koordinat varsa o kazanir; ulke ve
     * buyukluk bilgisi veri setinden tamamlanir.
     */
    fun find(code: String): Airport? {
        val curated = Airports[code]
        val fromData = byCode[code]
        return when {
            curated != null && fromData != null -> curated.copy(
                country = fromData.country,
                rank = fromData.rank,
                airportName = fromData.airportName,
            )
            curated != null -> curated
            else -> fromData
        }
    }

    /**
     * Sehir adi ya da IATA koduna gore arar. Basiyla eslesenler once, ardindan
     * buyuk havalimanlari gelir.
     */
    fun search(query: String, limit: Int = 30): List<Airport> {
        val q = normalizeForSearch(query.trim())
        if (q.isEmpty()) return emptyList()

        val hits = ArrayList<Pair<Int, Airport>>()
        val upper = q.uppercase(Locale.ROOT)
        for (i in loaded.indices) {
            val place = loaded[i]
            var best = Int.MAX_VALUE
            if (place.code == upper) best = 0
            for (key in searchKeys[i]) {
                val score = when {
                    key.startsWith(q) -> 1
                    key.contains(q) -> 3
                    else -> continue
                }
                if (score < best) best = score
            }
            if (best == Int.MAX_VALUE) continue
            hits += (best * 4 + place.rank) to place
        }
        return hits
            .sortedWith(compareBy({ it.first }, { it.second.city.length }))
            .take(limit)
            .map { withPreferredName(it.second) }
    }

    /**
     * Gosterilecek ad: once elle yazilmis Turkce ad, sonra Turkce karsilik sozlugu,
     * en son veri setindeki ad.
     */
    fun withPreferredName(place: Airport): Airport {
        val curated = Airports[place.code]?.city
        val turkish = TurkishCities[place.code]
        val name = curated ?: turkish ?: return place
        return place.copy(city = name)
    }
}
