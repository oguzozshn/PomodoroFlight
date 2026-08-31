package com.oguzozsahin.ucusmodu.data

data class Airport(val code: String, val city: String, val lat: Double, val lon: Double)

data class Preset(val from: String, val to: String, val nick: String) {
    val key: String get() = "$from-$to"
}

object Airports {

    val all: Map<String, Airport> = listOf(
        Airport("IST", "İstanbul", 41.28, 28.75),
        Airport("ESB", "Ankara", 40.13, 32.99),
        Airport("ADB", "İzmir", 38.29, 27.16),
        Airport("AYT", "Antalya", 36.90, 30.79),
        Airport("LHR", "Londra", 51.47, -0.45),
        Airport("CDG", "Paris", 49.01, 2.55),
        Airport("FCO", "Roma", 41.80, 12.25),
        Airport("BER", "Berlin", 52.36, 13.50),
        Airport("MAD", "Madrid", 40.47, -3.56),
        Airport("KEF", "Reykjavík", 63.99, -22.62),
        Airport("SVO", "Moskova", 55.97, 37.41),
        Airport("CMN", "Kazablanka", 33.37, -7.59),
        Airport("CAI", "Kahire", 30.11, 31.41),
        Airport("NBO", "Nairobi", -1.32, 36.93),
        Airport("CPT", "Cape Town", -33.97, 18.60),
        Airport("DXB", "Dubai", 25.25, 55.36),
        Airport("DOH", "Doha", 25.27, 51.61),
        Airport("DEL", "Delhi", 28.56, 77.10),
        Airport("BKK", "Bangkok", 13.69, 100.75),
        Airport("SIN", "Singapur", 1.36, 103.99),
        Airport("HKG", "Hong Kong", 22.31, 113.91),
        Airport("PEK", "Pekin", 40.08, 116.58),
        Airport("ICN", "Seul", 37.46, 126.44),
        Airport("HND", "Tokyo", 35.55, 139.78),
        Airport("SYD", "Sidney", -33.94, 151.18),
        Airport("AKL", "Auckland", -37.01, 174.79),
        Airport("HNL", "Honolulu", 21.32, -157.92),
        Airport("LAX", "Los Angeles", 33.94, -118.41),
        Airport("JFK", "New York", 40.64, -73.78),
        Airport("YYZ", "Toronto", 43.68, -79.63),
        Airport("MEX", "Meksiko", 19.44, -99.07),
        Airport("GRU", "São Paulo", -23.43, -46.47),
        Airport("EZE", "Buenos Aires", -34.82, -58.54),
        Airport("SCL", "Santiago", -33.39, -70.79),
    ).associateBy { it.code }

    private val trCollator: java.text.Collator =
        java.text.Collator.getInstance(java.util.Locale("tr", "TR"))

    /** Sehir adina gore siralanmis kod listesi (secim kutulari icin). */
    val codesByCity: List<String> = all.values
        .sortedWith { x, y -> trCollator.compare(x.city, y.city) }
        .map { it.code }

    operator fun get(code: String): Airport? = all[code]
}

object Presets {
    val list = listOf(
        Preset("IST", "ESB", "Kısa iç hat"),
        Preset("IST", "LHR", "Avrupa hattı"),
        Preset("IST", "JFK", "Atlantik geçişi"),
        Preset("CDG", "HND", "Kuzey kutbu üzerinden"),
        Preset("SCL", "AKL", "Pasifik boşluğu"),
        Preset("IST", "SYD", "Kanguru rotası"),
    )
}
