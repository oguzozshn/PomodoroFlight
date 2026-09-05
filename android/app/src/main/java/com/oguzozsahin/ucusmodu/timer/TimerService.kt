package com.oguzozsahin.ucusmodu.timer

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Sayac calisirken uygulamayi on planda tutar; geri sayimi bildirimde yasatir.
 * Durum bu serviste degil, [FlightEngine] icinde tutulur — servis yalnizca vitrindir.
 */
class TimerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collector: Job? = null
    private var started = false
    private var lastText: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val engine = FlightEngine.get(this)
        // startForegroundService sozlesmesi geregi her cagrida once on plana geciyoruz.
        promote(engine)

        when (intent?.action) {
            ACTION_PAUSE -> engine.pause()
            ACTION_SKIP -> engine.skip()
            ACTION_STOP -> {
                stopSelfSafely()
                return START_NOT_STICKY
            }
        }

        observe(engine)
        return START_STICKY
    }

    private fun promote(engine: FlightEngine) {
        if (started) return
        val notification = Notifications.ongoing(this, engine.state.value)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, Notifications.ID_ONGOING, notification, type)
        started = true
    }

    private fun observe(engine: FlightEngine) {
        if (collector != null) return
        collector = scope.launch {
            engine.state.collectLatest { state ->
                if (!state.running) {
                    stopSelfSafely()
                    return@collectLatest
                }
                if (!Notifications.canPost(this@TimerService)) return@collectLatest
                // Bildirim saniyede bir tazelenir; ara guncellemeler atlanir.
                val text = "${state.phase.key}|${state.remainingMs / 1000}"
                if (text == lastText) return@collectLatest
                lastText = text
                try {
                    NotificationManagerCompat.from(this@TimerService).notify(Notifications.ID_ONGOING, Notifications.ongoing(this@TimerService, state))
                } catch (e: SecurityException) {
                    // izin geri alinmis olabilir
                }
            }
        }
    }

    private fun stopSelfSafely() {
        collector?.cancel()
        collector = null
        started = false
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PAUSE = "com.oguzozsahin.ucusmodu.PAUSE"
        const val ACTION_SKIP = "com.oguzozsahin.ucusmodu.SKIP"
        const val ACTION_STOP = "com.oguzozsahin.ucusmodu.STOP"

        fun ensureRunning(context: Context) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, TimerService::class.java),
                )
            } catch (e: Exception) {
                // arka plandan servis baslatilamiyorsa sayac yine de bellekte isler
            }
        }

        fun ensureStopped(context: Context) {
            try {
                context.startService(
                    Intent(context, TimerService::class.java).setAction(ACTION_STOP),
                )
            } catch (e: Exception) {
                // servis zaten kapali
            }
        }
    }
}
