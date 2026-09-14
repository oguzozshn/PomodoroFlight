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
        // kayit bulunursa bacaklar rotanin kendi bacak boyuyla km'ye cevrilir.
        val fromV2 = prefs.getString(KEY, null)
        val raw = fromV2 ?: prefs.getString(LEGACY_KEY, null) ?: return Persisted()
        val legacy = fromV2 == null
        return try {
            val o = JSONObject(raw)
            val focus = o.optInt("focusMin", DEFAULT_FOCUS_MIN)
            val routeKey = o.optString("route", "IST-JFK")

            var log: List<LogEntry> = buildList {
                val arr = o.optJSONArray("log") ?: JSONArray()
                for (i in 0 until arr.length()) {
                    val e = arr.optJSONObject(i) ?: continue
                    val km = e.optInt("km")
                    val time = e.optLong("t")
                    add(
                        LogEntry(
                            routeKey = e.optString("r"),
                            km = km,
                            time = time,
                            // Eski satirlar yalniz varista yaziliyordu: tamamlanmis sayilir.
                            flown = e.optDouble("flown", km.toDouble()),
                            start = e.optLong("start", time),
                            done = e.optBoolean("done", true),
                        ),
                    )
                }
            }

            var flight = o.optJSONObject("flight")?.let { f ->
                ActiveFlight(
                    routeKey = f.optString("r"),
                    flown = f.optDouble("flown", 0.0).coerceAtLeast(0.0),
                    start = f.optLong("start", 0L),
                    landedAt = f.optLong("landed", 0L),
                ).takeIf { it.routeKey.isNotBlank() }
            }

            // Rota basina ilerleme tutan eski kayit tek etkin ucusa donusur.
            // Bir kez olur: donusen kayit asagida hemen yazilir.
            val progressJson = o.optJSONObject("progress")
            if (progressJson != null) {
                val progress = mutableMapOf<String, Double>()
                val keys = progressJson.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    progress[k] = if (legacy) {
                        Route.kmForLegs(k, progressJson.optInt(k, 0))
                    } else {
                        progressJson.optDouble(k, 0.0)
                    }
                }
                val (migrated, rows) = Flights.fromProgress(progress, routeKey, log, System.currentTimeMillis())
                if (flight == null) flight = migrated
                log = rows
            }

            val loaded = Persisted(
                routeKey = routeKey,
                flight = flight,
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
            if (progressJson != null || legacy) save(loaded)
            loaded
        } catch (e: Exception) {
            Persisted()
        }
    }

    fun save(p: Persisted) {
        val o = JSONObject()
        o.put("route", p.routeKey)
        p.flight?.let { f ->
            o.put(
                "flight",
                JSONObject()
                    .put("r", f.routeKey)
                    .put("flown", f.flown)
                    .put("start", f.start)
                    .put("landed", f.landedAt),
            )
        }
        val arr = JSONArray()
        for (e in p.log) {
            arr.put(
                JSONObject()
                    .put("r", e.routeKey)
                    .put("km", e.km)
                    .put("t", e.time)
                    .put("flown", e.flown)
                    .put("start", e.start)
                    .put("done", e.done),
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
