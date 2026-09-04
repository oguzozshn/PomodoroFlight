package com.oguzozsahin.ucusmodu.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.oguzozsahin.ucusmodu.audio.Chime
import com.oguzozsahin.ucusmodu.audio.ChimeKind
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.LogEntry
import com.oguzozsahin.ucusmodu.data.Persisted
import com.oguzozsahin.ucusmodu.data.Phase
import com.oguzozsahin.ucusmodu.data.Route
import com.oguzozsahin.ucusmodu.data.Store
import com.oguzozsahin.ucusmodu.data.formatNumber
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Sayacin tek otoritesi. Aktivite olse de servis ayakta kaldigi surece burasi yasar;
 * surec tamamen olduruldugunde durum diskten geri kurulur.
 */
class FlightEngine private constructor(context: Context) {

    private val app = context.applicationContext
    private val store = Store(app)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var data: Persisted = store.load()
    private var route: Route = Route.of(data.routeKey) ?: Route.of("IST-JFK")!!
    private var remaining: Long = data.remainingMs
    private var phase: Phase = data.phase
    private var running: Boolean = false
    private var endAt: Long = 0L
    private var ticker: Job? = null

    private val alarmManager: AlarmManager? =
        app.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<FlightUiState> = _state.asStateFlow()

    init {
        Notifications.ensureChannels(app)
        if (data.running && data.endAt > 0L) {
            // Surec olmusken gecen sureyi telafi et.
            running = true
            endAt = data.endAt
            remaining = (endAt - now()).coerceAtLeast(0L)
            var guard = 8
            while (running && now() >= endAt && guard-- > 0) {
                // Az once bitmisse anons hala anlamli; cok eskiyse sessizce kapat.
                val recent = now() - endAt < 5 * 60_000L
                finishPhase(notify = recent)
            }
            if (running) {
                // Yeniden baslatma sonrasi alarm kaybolmus olabilir; tazeliyoruz.
                scheduleAlarm(endAt)
                startTicker()
                TimerService.ensureRunning(app)
            }
        }
        publish()
    }

    // ---------------- kullanici eylemleri ----------------

    fun toggle() = if (running) pause() else start()

    fun start() {
        if (phase == Phase.FOCUS && currentState().arrived) return
        if (remaining <= 0L) remaining = currentState().phaseDurationMs
        running = true
        endAt = now() + remaining
        scheduleAlarm(endAt)
        startTicker()
        TimerService.ensureRunning(app)
        persist()
        publish()
    }

    fun pause() {
        if (running) remaining = (endAt - now()).coerceAtLeast(0L)
        running = false
        endAt = 0L
        cancelAlarm()
        stopTicker()
        TimerService.ensureStopped(app)
        persist()
        publish()
    }

    /** Sayaci bulundugu fazin basina dondurur. */
    fun reset() {
        pause()
        remaining = currentState().phaseDurationMs
        persist()
        publish()
    }

    /** Fazi erken bitirir; odak fazinda bacak tamamlanmis sayilir. */
    fun skip() {
        pause()
        finishPhase(notify = false)
        publish()
    }

    fun setRoute(key: String) {
        val next = Route.of(key) ?: return
        pause()
        route = next
        data = data.copy(routeKey = next.key)
        phase = Phase.FOCUS
        remaining = data.focusMin * 60_000L
        persist()
        publish()
    }

    fun setFocusMin(minutes: Int) {
        data = data.copy(focusMin = minutes)
        if (!running && phase == Phase.FOCUS) remaining = minutes * 60_000L
        persist()
        publish()
    }

    fun setBreakMin(minutes: Int) {
        data = data.copy(breakMin = minutes)
        if (!running && phase == Phase.BREAK) remaining = minutes * 60_000L
        persist()
        publish()
    }

    fun setAutoBreak(enabled: Boolean) {
        data = data.copy(autoBreak = enabled)
        persist()
        publish()
    }

    fun setPassenger(name: String) {
        data = data.copy(passenger = name.take(28))
        persist()
        publish()
    }

    /** Bilet basilirken kisa bir tik: yazici hissi. */
    fun vibrateTicket() {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        try {
            device.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 18, 60, 18, 60, 18, 60, 26),
                    intArrayOf(0, 90, 0, 90, 0, 90, 0, 120),
                    -1,
                ),
            )
        } catch (e: Exception) {
            // titresim desteklenmiyorsa sessizce gec
        }
    }

    /** Bilet koparilirken her tirtikta kisa bir tik. */
    fun vibrateTick() {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        try {
            device.vibrate(VibrationEffect.createOneShot(12L, 70))
        } catch (e: Exception) {
            // titresim desteklenmiyorsa sessizce gec
        }
    }

    fun setSound(enabled: Boolean) {
        data = data.copy(sound = enabled)
        persist()
        publish()
    }

    /** Gunlugu ve tum rota ilerlemesini siler. */
    fun clearLog() {
        pause()
        data = data.copy(log = emptyList(), progress = emptyMap(), totalMs = 0L, totalKm = 0.0, cycle = 0)
        phase = Phase.FOCUS
        remaining = data.focusMin * 60_000L
        persist()
        publish()
    }

    /**
     * Uyandiginda ya da alarm dustugunde cagrilir: gercek zamanla arayi kapatir.
     */
    fun syncNow() {
        if (!running) return
        remaining = (endAt - now()).coerceAtLeast(0L)
        var guard = 8
        while (running && now() >= endAt && guard-- > 0) {
            finishPhase(notify = true)
        }
        publish()
    }

    // ---------------- ic isleyis ----------------

    private fun finishPhase(notify: Boolean) {
        val before = currentState()
        if (phase == Phase.FOCUS) {
            val nextDone = (before.done + 1).coerceAtMost(route.legs)
            val progress = data.progress.toMutableMap()
            progress[route.key] = nextDone
            val arrivedNow = nextDone >= route.legs
            val log = if (arrivedNow) {
                listOf(LogEntry(route.key, route.km.roundToInt(), System.currentTimeMillis())) + data.log
            } else {
                data.log
            }
            data = data.copy(
                progress = progress,
                log = log,
                totalMs = data.totalMs + before.durationMsOf(Phase.FOCUS),
                totalKm = data.totalKm + route.kmPerLeg,
                cycle = (data.cycle + 1) % 4,
            )
            if (notify) {
                val kind = if (arrivedNow) ChimeKind.LAND else ChimeKind.LEVEL
                chime(kind)
                buzz(kind)
                if (arrivedNow) {
                    Notifications.alert(
                        app,
                        "İniş yapıldı · ${route.dest.city}",
                        "${route.origin.code} → ${route.dest.code} tamamlandı · " +
                            "${formatNumber(route.km)} km. Günlüğe damga düştü.",
                    )
                } else {
                    val left = (route.legs - nextDone).coerceAtLeast(0)
                    Notifications.alert(
                        app,
                        "Pomodoro tamam · mola",
                        "${formatNumber(route.kmPerLeg)} km yol aldın · " +
                            "${route.dest.city} için $left pomodoro kaldı.",
                    )
                }
            }
            setPhase(if (data.cycle == 0) Phase.LONG else Phase.BREAK)
            if (data.autoBreak && !arrivedNow) {
                start()
            } else {
                TimerService.ensureStopped(app)
            }
        } else {
            if (notify) {
                chime(ChimeKind.BOARDING)
                buzz(ChimeKind.BOARDING)
                Notifications.alert(app, "Mola bitti", "Kabin hazır — seyre devam edebilirsin.")
            }
            setPhase(Phase.FOCUS)
            TimerService.ensureStopped(app)
        }
        persist()
        publish()
    }

    private fun setPhase(next: Phase) {
        phase = next
        running = false
        endAt = 0L
        cancelAlarm()
        stopTicker()
        remaining = currentState().durationMsOf(next)
    }

    private fun chime(kind: ChimeKind) {
        if (!data.sound) return
        scope.launch(Dispatchers.Default) { Chime.play(kind) }
    }

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (app.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        app.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Faz degisiminde kisa bir titresim. Ses kapaliyken de calisir: kabin
     * anonsunun sessiz karsiligi.
     */
    private fun buzz(kind: ChimeKind) {
        val device = vibrator ?: return
        if (!device.hasVibrator()) return
        val timings = when (kind) {
            ChimeKind.LAND -> longArrayOf(0, 90, 80, 90, 80, 160)
            ChimeKind.LEVEL -> longArrayOf(0, 70, 90, 70)
            ChimeKind.BOARDING -> longArrayOf(0, 120)
        }
        val amplitudes = IntArray(timings.size) { if (it % 2 == 0) 0 else 160 }
        try {
            device.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } catch (e: Exception) {
            // titresim desteklenmiyorsa sessizce gec
        }
    }

    private fun startTicker() {
        stopTicker()
        ticker = scope.launch {
            while (isActive && running) {
                val left = endAt - now()
                if (left <= 0L) {
                    remaining = 0L
                    finishPhase(notify = true)
                } else {
                    remaining = left
                    publish()
                }
                delay(250L)
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }

    private fun now() = System.currentTimeMillis()

    private fun currentState() = snapshot()

    private fun snapshot() = FlightUiState(
        route = route,
        phase = phase,
        running = running,
        remainingMs = remaining,
        progress = data.progress,
        log = data.log,
        focusMin = data.focusMin,
        breakMin = data.breakMin,
        autoBreak = data.autoBreak,
        sound = data.sound,
        passenger = data.passenger,
        cycle = data.cycle,
        totalMs = data.totalMs,
        totalKm = data.totalKm,
    )

    private fun publish() {
        _state.value = snapshot()
    }

    private fun persist() {
        data = data.copy(
            routeKey = route.key,
            phase = phase,
            running = running,
            endAt = endAt,
            remainingMs = remaining,
        )
        store.save(data)
    }

    // ---------------- alarm ----------------

    private fun alarmPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        app,
        7,
        Intent(app, PhaseAlarmReceiver::class.java).setAction(PhaseAlarmReceiver.ACTION_PHASE_END),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Cihaz uykuya dalsa da fazin bitisini zamaninda yakalamak icin kesin alarm kurar.
     * Izin yoksa uyku dostu alarma duser.
     */
    private fun scheduleAlarm(at: Long) {
        val manager = alarmManager ?: return
        val pending = alarmPendingIntent()
        try {
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || manager.canScheduleExactAlarms()
            if (exact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            }
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private fun cancelAlarm() {
        alarmManager?.cancel(alarmPendingIntent())
    }

    companion object {
        @Volatile
        private var instance: FlightEngine? = null

        fun get(context: Context): FlightEngine =
            instance ?: synchronized(this) {
                instance ?: FlightEngine(context).also { instance = it }
            }
    }
}
