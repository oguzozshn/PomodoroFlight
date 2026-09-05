package com.oguzozsahin.ucusmodu.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Cihaz uykudayken fazin bitisini yakalar: motoru uyandirip gercek zamanla esitler.
 */
class PhaseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_PHASE_END) return
        FlightEngine.get(context).syncNow()
    }

    companion object {
        const val ACTION_PHASE_END = "com.oguzozsahin.ucusmodu.PHASE_END"
    }
}
