package com.oguzozsahin.ucusmodu.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** SharedPreferences uzerinde JSON olarak saklanan basit kalici depo. */
class Store(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): Persisted {
        // v2 ilerlemeyi km olarak tutuyor; v1 bacak sayisi tutuyordu. Eski
        // kayit bulunursa bacaklar km'ye cevrilir — v1'de her bacak tam olarak
        // bir varsayilan pomodoro, yani 850 km ediyordu.
        val fromV2 = prefs.getString(KEY, null)
        val raw = fromV2 ?: prefs.getString(LEGACY_KEY, null) ?: return Persisted()
        val legacy = fromV2 == null
        return try {
            val o = JSONObject(raw)
            val focus = o.optInt("focusMin", DEFAULT_FOCUS_MIN)

            val progress = mutableMapOf<String, Double>()
            o.optJSONObject("progress")?.let { p ->
                val it = p.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    progress[k] = if (legacy) {
                        Route.kmForLegs(k, p.optInt(k, 0))
                    } else {
                        p.optDouble(k, 0.0)
                    }
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

            Persisted(
                routeKey = o.optString("route", "IST-JFK"),
                progress = progress,
                log = log,
                focusMin = focus,
                breakMin = o.optInt("breakMin", 5),
                autoBreak = o.optBoolean("autoBreak", true),
                sound = o.optBoolean("sound", true),
                passenger = o.optString("passenger", ""),
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
        o.put("passenger", p.passenger)
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
        const val KEY = "state-v2"

        /** Ilerlemeyi bacak sayisi olarak tutan eski kayit. */
        const val LEGACY_KEY = "state-v1"
    }
}
