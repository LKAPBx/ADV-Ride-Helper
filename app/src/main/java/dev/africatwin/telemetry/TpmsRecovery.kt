package dev.africatwin.telemetry

import android.app.PendingIntent
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.*
import android.os.*

/** Filtered system scan can deliver packets after this process has been reclaimed. */
internal object TpmsRecovery {
    private fun prefs(c: Context) = c.getSharedPreferences("tire_recovery", 0)
    fun armed(c: Context) = prefs(c).getBoolean("armed", false) && TpmsAlerts.backgroundEnabled(c)
    private fun intent(c: Context) = PendingIntent.getBroadcast(c, 160,
        Intent(c, TireScanReceiver::class.java).setAction("dev.africatwin.telemetry.TIRE_PACKET"),
        PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
    fun arm(c: Context, force: Boolean = false) {
        if (!TpmsAlerts.backgroundEnabled(c)) { disarm(c); return }
        if (!TpmsMonitor.allowed(c)) return
        if (System.currentTimeMillis() < prefs(c).getLong("retryAfter", 0)) return
        try {
            val adapter = c.getSystemService(BluetoothManager::class.java).adapter ?: return
            if (!adapter.isEnabled) return
            val ids = listOf("front", "rear").map { TpmsStore(c).assigned(it) }.filter { BluetoothAdapter.checkBluetoothAddress(it) }.distinct().sorted()
            if (ids.isEmpty()) { disarm(c); return }
            val signature = ids.joinToString(",")
            if (!force && armed(c) && prefs(c).getString("ids", "") == signature) return
            adapter.bluetoothLeScanner?.stopScan(intent(c))
            val scanner = adapter.bluetoothLeScanner ?: return
            val result = scanner.startScan(ids.map { ScanFilter.Builder().setDeviceAddress(it).build() },
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), intent(c))
            prefs(c).edit().putBoolean("armed", result == 0).putString("ids", signature)
                .putLong("armedAt", System.currentTimeMillis()).putLong("retryAfter", if (result == 0) 0 else System.currentTimeMillis() + 30_000).apply()
            if (result != 0) c.getSharedPreferences("tire_diagnostics", 0).edit().putString("lastIssue", "Background scan request failed ($result)").apply()
        } catch (e: RuntimeException) {
            prefs(c).edit().putBoolean("armed", false).putLong("retryAfter", System.currentTimeMillis() + 30_000).apply()
            c.getSharedPreferences("tire_diagnostics", 0).edit().putString("lastIssue", "Background detection blocked (${e.javaClass.simpleName})").apply()
        }
    }
    fun disarm(c: Context) {
        if (TpmsMonitor.allowed(c)) runCatching { c.getSystemService(BluetoothManager::class.java).adapter?.bluetoothLeScanner?.stopScan(intent(c)) }
        prefs(c).edit().putBoolean("armed", false).apply()
    }
    fun stale(c: Context, time: Long?) = armed(c) && TpmsAge.idleStale(time?.let { System.currentTimeMillis() - it },
        System.currentTimeMillis() - prefs(c).getLong("armedAt", System.currentTimeMillis()))
    fun startFromUi(c: Context) {
        if (!TpmsAlerts.backgroundEnabled(c) || !TpmsMonitor.allowed(c)) return
        if (listOf("front", "rear").all { TpmsStore(c).assigned(it).isEmpty() }) return
        arm(c)
        runCatching { c.startForegroundService(Intent(c, TpmsBackgroundService::class.java)) }.onFailure {
            c.getSharedPreferences("tire_diagnostics", 0).edit().putString("lastIssue", "Monitoring start blocked (${it.javaClass.simpleName})").apply()
        }
    }
}

class TireScanReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!TpmsAlerts.backgroundEnabled(context) || !TpmsMonitor.allowed(context)) return
        val error = intent.getIntExtra(BluetoothLeScanner.EXTRA_ERROR_CODE, 0)
        if (error != 0) {
            TpmsRecovery.disarm(context)
            context.getSharedPreferences("tire_recovery", 0).edit().putLong("retryAfter", System.currentTimeMillis() + 30_000).apply()
            context.getSharedPreferences("tire_diagnostics", 0).edit().putString("lastIssue", "System BLE scan failed ($error); open app to retry").apply(); return
        }
        @Suppress("DEPRECATION")
        val results = intent.getParcelableArrayListExtra<ScanResult>(BluetoothLeScanner.EXTRA_LIST_SCAN_RESULT).orEmpty()
        results.forEach { TpmsMonitor.consume(context, it) }
    }
}

class TireRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED && intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1) != BluetoothAdapter.STATE_ON) {
            TpmsMonitor.reset(context, "Bluetooth restarted"); TpmsRecovery.disarm(context); return
        }
        if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED, BluetoothAdapter.ACTION_STATE_CHANGED)) {
            TpmsRecovery.arm(context, force = true)
            // No background foreground-service launch: the system scan delivers readings itself.
        }
    }
}
