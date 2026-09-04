package com.oguzozsahin.ucusmodu.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kaba (110m) ve ince (50m) kiyi seridi varliklarinin adlari. */
enum class CoastDetail(val asset: String) {
    COARSE("coastline_110m.bin"),
    FINE("coastline_50m.bin"),
}

/** Kiyi seritlerini varliklardan okur ve bellekte tutar. */
object Coastlines {

    private val cache = HashMap<CoastDetail, CoastlineShapes>()

    suspend fun load(context: Context, detail: CoastDetail): CoastlineShapes? =
        withContext(Dispatchers.IO) {
            synchronized(cache) { cache[detail] }?.let { return@withContext it }
            val parsed = try {
                context.applicationContext.assets.open(detail.asset).use {
                    CoastlineFormat.parse(it.readBytes())
                }
            } catch (e: Exception) {
                null
            }
            if (parsed != null) synchronized(cache) { cache[detail] = parsed }
            parsed
        }
}
