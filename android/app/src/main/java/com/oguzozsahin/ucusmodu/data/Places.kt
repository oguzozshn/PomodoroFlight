package com.oguzozsahin.ucusmodu.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private const val PLACES_ASSET = "places.bin"

/** Yer listesini varliktan okuyup kayit defterine yukler. */
object Places {

    suspend fun ensureLoaded(context: Context) {
        if (PlaceRegistry.isLoaded) return
        withContext(Dispatchers.IO) {
            val places = try {
                context.applicationContext.assets.open(PLACES_ASSET).use {
                    PlaceFormat.parse(it.readBytes())
                }
            } catch (e: Exception) {
                emptyList()
            }
            if (places.isNotEmpty()) PlaceRegistry.install(places)
        }
    }
}

/** ISO ulke kodunu Turkce ulke adina cevirir ("TR" -> "Türkiye"). */
fun countryName(isoCode: String): String {
    if (isoCode.length != 2) return isoCode
    val name = Locale("", isoCode).getDisplayCountry(Locale("tr", "TR"))
    return if (name.isNullOrBlank()) isoCode else name
}
