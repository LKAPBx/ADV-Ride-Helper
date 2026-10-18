package dev.africatwin.telemetry

import android.content.Context
import android.content.Intent

internal object TpmsFeature {
    fun enabled(c: Context) = c.getSharedPreferences("tpms_alerts", Context.MODE_PRIVATE).getBoolean("enabled", true)
    fun setEnabled(c: Context, enabled: Boolean) {
        c.getSharedPreferences("tpms_alerts", Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
        if (!enabled) {
            TpmsMonitor.reset(c, "TPMS disabled")
            TpmsRecovery.disarm(c)
            c.stopService(Intent(c, TpmsBackgroundService::class.java))
            TpmsAlerts.assignmentsChanged(c)
        } else {
            TpmsRecovery.startFromUi(c)
            if (RideStore(c).running) TpmsMonitor.start(c, "ride")
        }
        RideWidget.changed(c)
    }
}
