package com.oguzozsahin.ucusmodu.data

/**
 * Veri seti sehir adlarini yerel ya da Ingilizce yazar (Munich, Beijing, Casablanca).
 * Turkcede yerlesmis adi olanlar burada karsilanir; hem gosterimde hem aramada gecerlidir.
 * Yalnizca Turkcesi farkli olanlar listelenir.
 */
object TurkishCities {

    val byCode: Map<String, String> = mapOf(
        // Turkiye — veri seti bazi havalimanlarini ilcesiyle yazar
        "SAW" to "İstanbul", "COV" to "Adana", "KZR" to "Kütahya",
        "YEI" to "Bursa", "TEQ" to "Tekirdağ",

        // Avrupa
        "MUC" to "Münih", "VIE" to "Viyana", "ATH" to "Atina", "PRG" to "Prag",
        "WAW" to "Varşova", "BUD" to "Budapeşte", "OTP" to "Bükreş", "SOF" to "Sofya",
        "BEG" to "Belgrad", "SKP" to "Üsküp", "TIA" to "Tiran", "SJJ" to "Saraybosna",
        "LED" to "St. Petersburg",
        "SVO" to "Moskova", "DME" to "Moskova", "VKO" to "Moskova",
        "CPH" to "Kopenhag", "BRU" to "Brüksel", "LUX" to "Lüksemburg",
        "ZRH" to "Zürih", "GVA" to "Cenevre", "MXP" to "Milano", "LIN" to "Milano",
        "VCE" to "Venedik", "FLR" to "Floransa", "NAP" to "Napoli", "FCO" to "Roma",
        "CIA" to "Roma", "BCN" to "Barselona", "LIS" to "Lizbon", "SVQ" to "Sevilla",
        "VLC" to "Valensiya", "LHR" to "Londra", "LGW" to "Londra", "STN" to "Londra",
        "LTN" to "Londra", "MRS" to "Marsilya", "CGN" to "Köln", "SOU" to "Southampton",

        // Orta Dogu ve Kafkasya
        "TLV" to "Telaviv", "BEY" to "Beyrut", "DAM" to "Şam", "BGW" to "Bağdat",
        "EBL" to "Erbil", "KWI" to "Kuveyt", "AUH" to "Abu Dabi", "RUH" to "Riyad",
        "JED" to "Cidde", "MED" to "Medine", "MCT" to "Maskat", "SAH" to "Sana",
        "IKA" to "Tahran", "THR" to "Tahran", "TBS" to "Tiflis", "EVN" to "Erivan",
        "GYD" to "Bakü",

        // Afrika
        "CAI" to "Kahire", "ALG" to "Cezayir", "TUN" to "Tunus", 
        "CMN" to "Kazablanka", "RAK" to "Marakeş", "KRT" to "Hartum", "DAR" to "Darüsselam",
        "ACC" to "Akra",

        // Asya
        "TAS" to "Taşkent", "ALA" to "Almatı", "NQZ" to "Astana", "ASB" to "Aşkabat",
        "DYU" to "Duşanbe", "KBL" to "Kabil", "ISB" to "İslamabad",
        "KHI" to "Karaçi", "LHE" to "Lahor", "CCU" to "Kalküta", "CMB" to "Kolombo",
        "KTM" to "Katmandu", "DAC" to "Dakka", "SIN" to "Singapur", "CGK" to "Cakarta",
        "PEK" to "Pekin", "PKX" to "Pekin", "PVG" to "Şanghay", "SHA" to "Şanghay",
        "ICN" to "Seul", "GMP" to "Seul", "NRT" to "Tokyo", "UBN" to "Ulanbatur",

        // Amerika ve Okyanusya
        "ORD" to "Şikago", "MDW" to "Şikago", "IAD" to "Washington", "DCA" to "Washington",
        "YUL" to "Montreal", "MEX" to "Meksiko", "CCS" to "Karakas", "ASU" to "Asunsiyon",
        "SYD" to "Sidney",
    )

    operator fun get(code: String): String? = byCode[code]
}
