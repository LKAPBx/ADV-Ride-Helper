package dev.africatwin.telemetry

import android.content.*
import android.os.*

internal object TireDiagnostics {
    fun reset(context: Context) {
        val p = context.getSharedPreferences("tire_diagnostics", 0)
        val edit = p.edit()
        p.all.keys.filter { key -> listOf("count:", "intervals:", "mean:", "max:", "last:").any(key::startsWith) }.forEach(edit::remove)
        edit.apply()
    }
    fun packet(context: Context, id: String, time: Long, previous: Long) {
        val p = context.getSharedPreferences("tire_diagnostics", 0)
        val old = PacketTiming(p.getLong("count:$id", 0), p.getLong("intervals:$id", 0),
            p.getString("mean:$id", "0")?.toDoubleOrNull() ?: 0.0, p.getLong("max:$id", 0))
        val next = old.add(if (previous > 0 && old.count > 0) time - previous else 0)
        p.edit().putLong("count:$id", next.count).putLong("intervals:$id", next.intervalCount)
            .putString("mean:$id", next.meanMillis.toString()).putLong("max:$id", next.maxMillis).putLong("last:$id", time).apply()
    }
    fun report(context: Context): String {
        val p = context.getSharedPreferences("tire_diagnostics", 0)
        val store = TpmsStore(context)
        return "ADV Ride Helper • Tire monitoring report\n${TpmsMonitor.health(context)}\n\n" +
            listOf("front", "rear").joinToString("\n\n") { wheel ->
                val id = store.assigned(wheel)
                "$wheel • ${id.ifEmpty { "Not assigned" }}\n${store.wheel(wheel)}\nBattery health: ${store.battery(wheel)}\n" +
                    "Accepted packets: ${p.getLong("count:$id", 0)}; measured intervals: ${p.getLong("intervals:$id", 0)}\n" +
                    "Mean gap: ${((p.getString("mean:$id", "0")?.toDoubleOrNull() ?: 0.0) / 1000).toInt()}s; max gap: ${p.getLong("max:$id", 0) / 1000}s\n" +
                    "Battery evidence: ${store.reading(id)?.first?.batteryEvidence ?: "No supported battery field captured"}"
            } + "\n\nUser reference reported October 15, 2026: official FOBO app showed four of four battery bars for BOTH sensors. This is a reference observation, not a live decoded battery result.\n\nPacket gaps can reflect advertising intervals, sleep, reception or Android delivery. They do not measure sensor battery drain. Battery mapping needs comparison with the official FOBO app; Bike 2 experimental percentage uses byte6 × 2 by user hypothesis, not verified battery health. Values outside 0–100 are rejected; no battery alarms use this estimate.\n\n" + PhonePower.report(context)
    }
}

internal object PhonePower {
    private fun boot(c: Context) = android.provider.Settings.Global.getInt(c.contentResolver, android.provider.Settings.Global.BOOT_COUNT, -1)
    private fun sample(c: Context): Pair<Int, Int> {
        val battery = c.getSystemService(BatteryManager::class.java)
        return battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) to battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)
    }
    fun begin(c: Context) {
        val (percent, charge) = sample(c)
        val plugged = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        c.getSharedPreferences("power_observation", 0).edit().putLong("start", SystemClock.elapsedRealtime())
            .putInt("boot", boot(c)).putInt("percent", percent).putInt("charge", charge).putInt("plugged", plugged).remove("report").apply()
    }
    fun finish(c: Context) {
        val p = c.getSharedPreferences("power_observation", 0)
        val start = p.getLong("start", -1); val elapsed = SystemClock.elapsedRealtime() - start
        require(start >= 0 && elapsed > 0 && p.getInt("boot", -2) == boot(c)) { "Start a new observation first (also after a phone restart)." }
        val (percent, charge) = sample(c)
        val plugged = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        val startCharge = p.getInt("charge", Int.MIN_VALUE)
        val chargeText = if (charge > 0 && startCharge > 0) "Charge-counter change: ${(charge - startCharge) / 1000.0} mAh" else "Charge counter unavailable"
        fun level(value: Int) = if (value in 0..100) "$value%" else "unavailable"
        p.edit().putString("report", "Phone observation: ${elapsed / 60_000} minutes\nBattery: ${level(p.getInt("percent", -1))} → ${level(percent)}\n$chargeText\nCharging at endpoints: ${p.getInt("plugged", 0) != 0} / ${plugged != 0}\nWhole-phone measurement, not energy attributable to this app. Charging, screen, other apps and radio conditions affect the result. Compare equal-duration idle tests with monitoring on/off and GPS stopped.").remove("start").apply()
    }
    fun report(c: Context): String {
        val p = c.getSharedPreferences("power_observation", 0)
        return p.getString("report", null) ?: if (p.contains("start")) "Phone battery observation in progress" else "No phone battery observation recorded"
    }
}
