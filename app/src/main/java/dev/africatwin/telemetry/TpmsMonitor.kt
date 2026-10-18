package dev.africatwin.telemetry

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.*
import java.util.Locale
import org.json.JSONObject

internal class TpmsStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("tpms", Context.MODE_PRIVATE)
    fun assigned(wheel: String) = prefs.getString(wheel, "").orEmpty()
    fun reading(id: String): Pair<FoboCandidate, Long>? = runCatching {
        val json = JSONObject(prefs.getString("reading:$id", "")!!)
        FoboCandidate(json.getInt("kpa"), if (json.has("celsius")) json.getInt("celsius") else null,
            json.optString("batteryEvidence").takeIf { it.isNotEmpty() }, json.optDouble("experimentalVolts", Double.NaN).takeIf { it.isFinite() },
            if (json.has("experimentalBatteryPercent")) json.optInt("experimentalBatteryPercent", -1).takeIf { it in 0..100 } else null) to json.getLong("time")
    }.getOrNull()
    fun markTimedOut(ids: Set<String>, time: Long) {
        val edit = prefs.edit()
        ids.forEach { edit.putLong("staleAfter:$it", time) }
        edit.apply()
    }
    fun stale(id: String): Boolean {
        val time = reading(id)?.second
        val now = System.currentTimeMillis()
        val timeout = prefs.getLong("staleAfter:$id", 0)
        // A missed short manual refresh never expires an otherwise recent reading.
        return (time?.let { TpmsAge.idleStale(now - it, 0) } ?: (timeout > 0 && now - timeout >= TpmsAge.STALE_MILLIS)) ||
            TpmsMonitor.backgroundReadingStale(time) || TpmsRecovery.stale(context, time)
    }
    fun record(id: String, value: FoboCandidate, time: Long): Boolean {
        if (!TpmsFeature.enabled(context)) return false
        val previous = runCatching { JSONObject(prefs.getString("reading:$id", "")!!).optLong("time", 0) }.getOrDefault(0)
        if (time <= previous) return false
        val json = JSONObject().put("kpa", value.kpa).put("time", time)
        value.celsius?.let { json.put("celsius", it) }
        value.batteryEvidence?.let { json.put("batteryEvidence", it) }
        value.experimentalVolts?.let { json.put("experimentalVolts", it) }
        value.experimentalBatteryPercent?.let { json.put("experimentalBatteryPercent", it) }
        prefs.edit().putString("reading:$id", json.toString()).apply()
        TireDiagnostics.packet(context, id, time, previous)
        if (!stale(id) && System.currentTimeMillis() - time in 0L..30_000L) TpmsAlerts.check(context, id, value)
        return true
    }
    fun warning(wheel: String): Int {
        val json = runCatching { JSONObject(prefs.getString("reading:${assigned(wheel)}", "")!!) }.getOrNull() ?: return 0
        return TpmsAge.warning(json.optInt("kpa"), TpmsAlerts.threshold(context, wheel))
    }
    fun wheel(wheel: String): String {
        val id = assigned(wheel)
        if (id.isEmpty()) return "Not assigned"
        val json = runCatching { JSONObject(prefs.getString("reading:$id", "")!!) }.getOrNull()
            ?: return if (stale(id)) "Stale • no reading received" else "Waiting for sensor"
        val time = json.optLong("time", 0)
        val age = System.currentTimeMillis() - time
        val value = FoboCandidate(json.optInt("kpa"), if (json.has("celsius")) json.optInt("celsius") else null)
        val units = AppUnits.get(context)
        val numbers = "${units.pressure(value.psi)} • ${units.temperature(value.celsius)}"
        val warning = when (TpmsAge.warning(value.kpa, TpmsAlerts.threshold(context, wheel))) { 2 -> "ZERO • "; 1 -> "LOW • "; else -> "" }
        val timedOut = stale(id)
        val ageText = if (timedOut) TpmsAge.label(age).removePrefix("Stale • ") else TpmsAge.label(age)
        return "${if (timedOut) "STALE • " else ""}$warning$numbers\n$ageText"
    }
    fun summary() = "Front: ${wheel("front").replace('\n', ' ')}\nRear: ${wheel("rear").replace('\n', ' ')}"
    fun battery(wheel: String): String {
        val candidate = reading(assigned(wheel))?.first
        candidate?.experimentalBatteryPercent?.let { return "$it%${if (stale(assigned(wheel))) " • stale" else ""}" }
        return candidate?.experimentalVolts?.let { "Unknown health • experimental ${"%.1f".format(java.util.Locale.US, it)} V (unverified)" }
            ?: "Unknown • Bike 2 battery encoding unverified"
    }
}

/** Shared listening: tracking owns a continuous scan; visible manual refreshes own bounded scans. */
internal object TpmsMonitor {
    private val handler = Handler(Looper.getMainLooper())
    private val owners = mutableMapOf<String, Runnable?>()
    private var scanner: BluetoothLeScanner? = null
    private var app: Context? = null
    private var retryAt = 0L
    fun listening() = scanner != null && owners.isNotEmpty()
    fun health(context: Context): String {
        if (!TpmsFeature.enabled(context)) return "TPMS disabled"
        if (!allowed(context)) return "Tire monitoring: Nearby devices/location permission missing"
        val on = runCatching { context.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true }.getOrDefault(false)
        if (!on) return "Tire monitoring: Bluetooth off"
        if (listOf("front", "rear").all { TpmsStore(context).assigned(it).isEmpty() }) return "Tire monitoring: assign sensors"
        if (listening()) return "Tire monitoring: listening • low-power BLE"
        if (TpmsRecovery.armed(context)) return "Tire monitoring: background detection requested • foreground listener stopped"
        val issue = context.getSharedPreferences("tire_diagnostics", 0).getString("lastIssue", "Open app to restart")
        return "Tire monitoring: stopped • $issue"
    }
    fun reset(context: Context, reason: String) {
        owners.keys.toList().forEach(::stop)
        context.getSharedPreferences("tire_diagnostics", 0).edit().putString("lastIssue", reason).apply()
    }
    private var backgroundStartedAt = 0L
    fun backgroundReadingStale(time: Long?): Boolean = owners.containsKey("background") &&
        TpmsAge.idleStale(time?.let { System.currentTimeMillis() - it }, SystemClock.elapsedRealtime() - backgroundStartedAt)
    fun retainBackgroundStaleness(context: Context) {
        val store = TpmsStore(context)
        setOf(store.assigned("front"), store.assigned("rear")).filter { it.isNotEmpty() }.forEach { id ->
            val time = store.reading(id)?.second
            if (backgroundReadingStale(time)) store.markTimedOut(setOf(id), time ?: System.currentTimeMillis())
        }
    }
    private data class Poll(val context: Context, val window: TpmsPollWindow, val timeout: Runnable)
    private val polls = mutableMapOf<String, Poll>()
    fun poll(context: Context, owner: String): String {
        if (!TpmsFeature.enabled(context)) return "TPMS disabled"
        if (polls.containsKey(owner)) return "Checking both assigned tire sensors • up to 15 seconds"
        val store = TpmsStore(context)
        val ids = setOf(store.assigned("front"), store.assigned("rear")).filter { it.isNotEmpty() }.toSet()
        if (ids.isEmpty()) return "Assign Front/Rear sensors in Settings → Advanced → Scan TPMS sensors."
        val window = TpmsPollWindow(ids, SystemClock.elapsedRealtimeNanos())
        val timeout = Runnable { finishPoll(owner, timedOut = true) }
        polls[owner] = Poll(context.applicationContext, window, timeout)
        handler.postDelayed(timeout, 15_000)
        val status = start(context, owner)
        return if (status == "Listening for tire sensors") "Checking both assigned tire sensors • up to 15 seconds" else status
    }
    private fun finishPoll(owner: String, timedOut: Boolean) {
        val poll = polls.remove(owner) ?: return
        handler.removeCallbacks(poll.timeout)
        if (timedOut) TpmsStore(poll.context).markTimedOut(poll.window.missing, System.currentTimeMillis())
        stop(owner)
    }
    fun permissions() = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun allowed(context: Context) = permissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    fun start(context: Context, owner: String, duration: Long? = null): String {
        if (!TpmsFeature.enabled(context)) return "TPMS disabled"
        val store = TpmsStore(context)
        if (store.assigned("front").isEmpty() && store.assigned("rear").isEmpty()) return "Assign Front/Rear sensors in Settings → Advanced → Scan TPMS sensors."
        if (!allowed(context)) return "Allow Nearby devices access on the phone for tire monitoring."
        if (SystemClock.elapsedRealtime() < retryAt) return "Tire scan retry pending"
        if (owners.containsKey(owner) && scanner != null) return "Listening for tire sensors"
        try {
            val adapter = context.getSystemService(BluetoothManager::class.java).adapter
            if (adapter == null || !adapter.isEnabled) return "Turn on Bluetooth for tire monitoring."
            if (scanner == null) {
                val next = adapter.bluetoothLeScanner ?: return "BLE scanning unavailable"
                app = context.applicationContext
                val filters = listOf(
                    ScanFilter.Builder().setManufacturerData(0x0127, byteArrayOf()).build(),
                    ScanFilter.Builder().setServiceData(ParcelUuid.fromString("00000126-0000-1000-8000-00805f9b34fb"), byteArrayOf()).build(),
                    ScanFilter.Builder().setServiceData(ParcelUuid.fromString("0000012a-0000-1000-8000-00805f9b34fb"), byteArrayOf()).build())
                next.startScan(filters, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_POWER).build(), callback)
                scanner = next
            }
            val timeout = duration?.let { Runnable { stop(owner) } }
            if (owner == "background") backgroundStartedAt = SystemClock.elapsedRealtime()
            owners[owner] = timeout
            timeout?.let { handler.postDelayed(it, duration!!) }
            return if (duration != null) "Refreshing tire readings for 2 minutes" else "Listening for tire sensors"
        } catch (error: RuntimeException) {
            retryAt = SystemClock.elapsedRealtime() + 30_000
            context.getSharedPreferences("tire_diagnostics", 0).edit().putString("lastIssue", error.javaClass.simpleName).apply()
            return "Tire scan unavailable; check Bluetooth and Nearby devices access."
        }
    }
    fun stop(owner: String) {
        if (owner == "background") backgroundStartedAt = 0
        polls.remove(owner)?.let { handler.removeCallbacks(it.timeout) }
        owners.remove(owner)?.let(handler::removeCallbacks)
        if (owners.isEmpty()) {
            try { scanner?.stopScan(callback) } catch (_: RuntimeException) { }
            scanner = null; app = null
        }
    }
    fun consume(context: Context, result: ScanResult) {
        if (!TpmsFeature.enabled(context)) return
            try {
                val id = result.device.address
                val store = TpmsStore(context)
                if (id != store.assigned("front") && id != store.assigned("rear")) return
                val record = result.scanRecord ?: return
                val bike2 = record.getServiceData(ParcelUuid.fromString("00000126-0000-1000-8000-00805f9b34fb"))
                val service = record.getServiceData(ParcelUuid.fromString("0000012a-0000-1000-8000-00805f9b34fb"))
                val candidate = if (bike2 != null) FoboDecoder.decodeBike2Pressure(bike2, id)
                    else if (service != null) FoboDecoder.decode(service, true)
                    else record.getManufacturerSpecificData(0x0127)?.let { FoboDecoder.decode(it, false) }
                val age = SystemClock.elapsedRealtimeNanos() - result.timestampNanos
                if (candidate != null && age in 0L..120_000_000_000L) {
                    val accepted = store.record(id, candidate, System.currentTimeMillis() - age / 1_000_000)
                    if (accepted) {
                        val now = SystemClock.elapsedRealtimeNanos()
                        polls.toMap().forEach { (owner, poll) ->
                            if (poll.window.observe(id, result.timestampNanos, now)) finishPoll(owner, timedOut = false)
                        }
                    }
                }
            } catch (_: RuntimeException) { }
    }
    private val callback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) { handler.post { app?.let { consume(it, result) } } }
        override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { onScanResult(0, it) } }
        override fun onScanFailed(code: Int) { handler.post {
            app?.getSharedPreferences("tire_diagnostics", 0)?.edit()?.putString("lastIssue", "BLE scan failed ($code); retry in 30 seconds")?.apply()
            retryAt = SystemClock.elapsedRealtime() + 30_000
            owners.values.filterNotNull().forEach(handler::removeCallbacks)
            owners.clear(); scanner = null; app = null
        } }
    }
}
