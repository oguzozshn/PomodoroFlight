package com.oguzozsahin.ucusmodu.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Kurede cizilen alanlar: kara parcalari ve goller. */
enum class WorldAsset(val asset: String) {
    LAND_COARSE("land_110m.bin"),
    LAND_FINE("land_50m.bin"),
    LAKES("lakes_110m.bin"),
}

/** Dunya sekillerini varliklardan okur ve bellekte tutar. */
object WorldShapes {

    private val cache = HashMap<WorldAsset, ShapeSet>()

    suspend fun load(context: Context, detail: WorldAsset): ShapeSet? =
        withContext(Dispatchers.IO) {
            synchronized(cache) { cache[detail] }?.let { return@withContext it }
            val parsed = try {
                context.applicationContext.assets.open(detail.asset).use {
                    ShapeFormat.parse(it.readBytes())
                }
            } catch (e: Exception) {
                null
            }
            if (parsed != null) synchronized(cache) { cache[detail] = parsed }
            parsed
        }
}
