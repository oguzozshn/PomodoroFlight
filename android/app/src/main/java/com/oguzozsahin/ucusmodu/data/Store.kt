package com.oguzozsahin.ucusmodu.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Uygulamanin kalici durumu. Web surumundeki localStorage kaydinin birebir karsiligi,
 * ustune sureci hayatta kalan sayac alanlari (running/endAt) eklendi.
 */
data class Persisted(
    val routeKey: String = "IST-JFK",
    val progress: Map<String, Int> = emptyMap(),
    val log: List<LogEntry> = emptyList(),
    val focusMin: Int = 25,
    val breakMin: Int = 5,
    val autoBreak: Boolean = true,
    val sound: Boolean = true,
    val cycle: Int = 0,
    val totalMs: Long = 0L,
    val totalKm: Double = 0.0,
    val phase: Phase = Phase.FOCUS,
    val running: Boolean = false,
    val endAt: Long = 0L,
    val remainingMs: Long = 25 * 60_000L,
)

/** SharedPreferences uzerinde JSON olarak saklanan basit kalici depo. */
class Store(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): Persisted {
        val raw = prefs.getString(KEY, null) ?: return Persisted()
        return try {
            val o = JSONObject(raw)

            val progress = mutableMapOf<String, Int>()
            o.optJSONObject("progress")?.let { p ->
                val it = p.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    progress[k] = p.optInt(k, 0)
                }
            }

            val log = mutableListOf<LogEntry>()
            val arr = o.optJSONArray("log") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val e = arr.optJSONObject(i) ?: continue
                log += LogEntry(
                    routeKey = e.optString("r"),
                    km = e.optInt("km"),
                    time = e.optLong("t"),
                )
            }

            val focus = o.optInt("focusMin", 25)
            Persisted(
                routeKey = o.optString("route", "IST-JFK"),
                progress = progress,
                log = log,
                focusMin = focus,
                breakMin = o.optInt("breakMin", 5),
                autoBreak = o.optBoolean("autoBreak", true),
                sound = o.optBoolean("sound", true),
                cycle = o.optInt("cycle", 0),
                totalMs = o.optLong("totalMs", 0L),
                totalKm = o.optDouble("totalKm", 0.0),
                phase = Phase.fromKey(o.optString("phase", Phase.FOCUS.key)),
                running = o.optBoolean("running", false),
                endAt = o.optLong("endAt", 0L),
                remainingMs = o.optLong("remainingMs", focus * 60_000L),
            )
        } catch (e: Exception) {
            Persisted()
        }
    }

    fun save(p: Persisted) {
        val o = JSONObject()
        o.put("route", p.routeKey)
        val prog = JSONObject()
        for ((k, v) in p.progress) prog.put(k, v)
        o.put("progress", prog)
        val arr = JSONArray()
        for (e in p.log) {
            arr.put(
                JSONObject()
                    .put("r", e.routeKey)
                    .put("km", e.km)
                    .put("t", e.time),
            )
        }
        o.put("log", arr)
        o.put("focusMin", p.focusMin)
        o.put("breakMin", p.breakMin)
        o.put("autoBreak", p.autoBreak)
        o.put("sound", p.sound)
        o.put("cycle", p.cycle)
        o.put("totalMs", p.totalMs)
        o.put("totalKm", p.totalKm)
        o.put("phase", p.phase.key)
        o.put("running", p.running)
        o.put("endAt", p.endAt)
        o.put("remainingMs", p.remainingMs)
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    private companion object {
        const val PREFS = "ucus-modu"
        const val KEY = "state-v1"
    }
}
