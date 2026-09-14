package com.oguzozsahin.ucusmodu.timer

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.oguzozsahin.ucusmodu.MainActivity
import com.oguzozsahin.ucusmodu.R
import com.oguzozsahin.ucusmodu.data.FlightUiState
import com.oguzozsahin.ucusmodu.data.Phase
import com.oguzozsahin.ucusmodu.data.formatClock

object Notifications {

    const val CHANNEL_ONGOING = "ucus_seyir"
    const val CHANNEL_ALERT = "ucus_uyari"
    const val ID_ONGOING = 1001
    const val ID_ALERT = 1002

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val ongoing = NotificationChannel(
            CHANNEL_ONGOING,
            "Uçuş sayacı",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Seyir ve mola sırasında görünen geri sayım"
            setShowBadge(false)
            setSound(null, null)
        }

        val alert = NotificationChannel(
            CHANNEL_ALERT,
            "Kabin anonsu",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Bir pomodoro ya da mola bittiğinde çalar"
        }

        manager.createNotificationChannel(ongoing)
        manager.createNotificationChannel(alert)
    }

    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun contentIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceAction(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, TimerService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Sayac calisirken duran, her saniye tazelenen bildirim. */
    fun ongoing(context: Context, state: FlightUiState): Notification {
        val title = when (state.phase) {
            Phase.FOCUS -> "Seyir halinde · ${state.route.origin.code} → ${state.route.dest.code}"
            Phase.BREAK -> "Mola · kabin ışıkları kısık"
            Phase.LONG -> "Uzun mola · aktarma"
        }
        val text = if (state.phase == Phase.FOCUS) {
            "${formatClock(state.remainingMs)} · varışa ${state.legsLeft} pomodoro"
        } else {
            "${formatClock(state.remainingMs)} · mola bitince seyre devam"
        }

        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(R.drawable.ic_plane)
            .setContentTitle(title)
            .setContentText(text)
            .setSubText(state.route.flightNo)
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .addAction(0, "Beklet", serviceAction(context, TimerService.ACTION_PAUSE, 11))
            .apply {
                // Odak oturumu atlanamaz; yalniz mola atlanabilir.
                if (state.phase != Phase.FOCUS) {
                    addAction(0, "Molayı atla", serviceAction(context, TimerService.ACTION_SKIP, 12))
                }
            }
            .build()
    }

    /** Faz bittiginde tek seferlik anons. */
    fun alert(context: Context, title: String, text: String) {
        if (!canPost(context)) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(R.drawable.ic_plane)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent(context))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID_ALERT, notification)
        } catch (e: SecurityException) {
            // bildirim izni yoksa sessizce gec
        }
    }
}
