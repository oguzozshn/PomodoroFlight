package com.oguzozsahin.ucusmodu.data

import android.content.Context

/** Ucus sirasinda pencereden ne gorunecegi. */
enum class WindowMode(val key: String, val title: String, val hint: String) {
    /** Rotanin o anki noktasindan, gercek saate gore cizilen gokyuzu. */
    REALTIME("realtime", "Gerçek zamanlı", "Rotanın o anki noktası, gerçek gün ışığı"),

    /** Giris ekranindaki kure; ucagi yukaridan izler. */
    GLOBE("globe", "Küre", "Uçağı yukarıdan izleyen dünya haritası"),

    /** Kullanicinin sectigi gercek pencere videosu, dongude. */
    VIDEO("video", "Video", "Kendi seçtiğiniz pencere görüntüsü, döngüde");

    companion object {
        fun fromKey(key: String): WindowMode =
            values().firstOrNull { it.key == key } ?: REALTIME
    }
}

/** Pencerenin kullanici tercihleri. */
data class WindowPrefs(
    val mode: WindowMode = WindowMode.REALTIME,
    /** Kullanicinin sectigi videonun içerik adresi; bos ise henuz secilmemis. */
    val videoUri: String = "",
    val showWing: Boolean = true,
    val showFrame: Boolean = true,
)

/**
 * Pencere tercihleri ayri bir dosyada tutulur; sayacin dogrulanmis kaydina
 * (bkz. [Store]) hic dokunmaz.
 */
class WindowStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): WindowPrefs = WindowPrefs(
        mode = WindowMode.fromKey(prefs.getString(KEY_MODE, "") ?: ""),
        videoUri = prefs.getString(KEY_VIDEO, "") ?: "",
        showWing = prefs.getBoolean(KEY_WING, true),
        showFrame = prefs.getBoolean(KEY_FRAME, true),
    )

    fun save(value: WindowPrefs) {
        prefs.edit()
            .putString(KEY_MODE, value.mode.key)
            .putString(KEY_VIDEO, value.videoUri)
            .putBoolean(KEY_WING, value.showWing)
            .putBoolean(KEY_FRAME, value.showFrame)
            .apply()
    }

    private companion object {
        const val PREFS = "ucus-modu-pencere"
        const val KEY_MODE = "mode"
        const val KEY_VIDEO = "videoUri"
        const val KEY_WING = "wing"
        const val KEY_FRAME = "frame"
    }
}
