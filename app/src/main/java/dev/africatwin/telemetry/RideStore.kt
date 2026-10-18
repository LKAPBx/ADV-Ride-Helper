package dev.africatwin.telemetry

import android.content.Context
import java.util.Locale
import android.os.SystemClock

internal class RideStore(private val context: Context) {
    private val prefs = FuelRideState.preferences(context)
    val meters get() = prefs.getString("meters", "0")?.toDoubleOrNull()?.takeIf { it.isFinite() && it >= 0 } ?: 0.0
    val gap get() = prefs.getBoolean("gap", false)
    val heartbeat get() = prefs.getLong("heartbeat", 0)
    val running get() = prefs.getBoolean("running", false) && System.currentTimeMillis() - heartbeat in 0L..30_000L
    val requestedRunning get() = prefs.getBoolean("running", false)
    val lastFixTime get() = prefs.getLong("lastFix", 0)
    val lastGpsSignal get() = prefs.getLong("gpsSignal", 0)
    fun validGpsSignal() { prefs.edit().putLong("gpsSignal", System.currentTimeMillis()).apply() }
    val status get() = prefs.getString("status", "Tracking stopped") ?: "Tracking stopped"
    val manualMiles get() = prefs.getString("manualMiles", "0")?.toDoubleOrNull() ?: 0.0
    val sessionMeters get() = prefs.getString("sessionMeters", "0")?.toDoubleOrNull() ?: 0.0
    val estimatedMeters get() = prefs.getString("estimatedMeters", "0")?.toDoubleOrNull() ?: 0.0
    val sessionEstimatedMeters get() = prefs.getString("sessionEstimatedMeters", "0")?.toDoubleOrNull() ?: 0.0
    private fun estimatedText(value: Double) = if (value > 0) "\nIncludes ${AppUnits.get(context).distance(value / 1609.344, 2)} estimated across GPS gaps" else ""
    val sessionElapsed get() = SessionCounters.elapsed(prefs.getLong("sessionStart", -1), SystemClock.elapsedRealtime(), prefs.getLong("sessionElapsed", 0), running)
    val lastSummary get(): String {
        val units = AppUnits.get(context)
        if (prefs.contains("lastSessionMeters")) {
            val start = prefs.getString("lastSessionStartLabel", "").orEmpty()
            val distance = prefs.getString("lastSessionMeters", "0")?.toDoubleOrNull() ?: 0.0
            val fuel = prefs.getString("lastSessionGallons", null)?.toDoubleOrNull()
            return "$start\n${units.session(distance, prefs.getLong("lastSessionElapsed", 0))}\n${fuel?.let { units.volume(it) + " used" } ?: "Fuel used unavailable"}" + estimatedText(prefs.getString("lastSessionEstimatedMeters", "0")?.toDoubleOrNull() ?: 0.0)
        }
        return units.legacySummary((prefs.getString("lastSummary", "No completed session yet") ?: "No completed session yet")
        .replace("US gal estimated used", "USG used").replace("US gal used", "USG used")
        .replace(Regex("(\\d+(?:[.,]\\d+)?) USG(?! used)"), "$1 USG used")
        .replace(Regex(" • (?=\\d+(?:[.,]\\d+)? USG used)"), "\n"))
    }
    fun healthText(): String = when {
        requestedRunning && !running -> "Tracking interrupted • restart on phone"
        !running -> "Tracking stopped"
        lastGpsSignal == 0L || System.currentTimeMillis() - lastGpsSignal !in 0L..30_000L -> "Waiting for GPS • distance may be missing"
        lastFixTime == 0L || System.currentTimeMillis() - lastFixTime > 30_000L -> "GPS ready • waiting for accepted movement"
        else -> "Recording GPS distance"
    }
    private fun elapsedNow(): Long {
        val start = prefs.getLong("sessionStart", -1)
        val now = SystemClock.elapsedRealtime()
        return SessionCounters.elapsed(start, now, prefs.getLong("sessionElapsed", 0), true)
    }
    fun sessionText(): String = AppUnits.get(context).session(sessionMeters, sessionElapsed) + estimatedText(sessionEstimatedMeters)
    fun currentSessionText(): String = if (running) sessionText() else AppUnits.get(context).session(0.0, 0)
    fun heartbeat() { prefs.edit().putLong("heartbeat", System.currentTimeMillis()).putLong("sessionElapsed", elapsedNow()).apply() }
    fun start() { RideDisconnect.clear(context); prefs.edit().putBoolean("running", true).putLong("heartbeat", System.currentTimeMillis())
        .putLong("sessionStartedAt", System.currentTimeMillis())
        .putLong("sessionStart", SystemClock.elapsedRealtime()).putLong("sessionElapsed", 0).putString("sessionMeters", "0")
        .putString("sessionEstimatedMeters", "0")
        .putBoolean("gap", gap || requestedRunning || lastFixTime != 0L).putLong("lastFix", 0).putLong("gpsSignal", 0).putString("status", "Waiting for GPS").apply() }
    fun stop(message: String) {
        RideDisconnect.clear(context)
        val wasRunning = requestedRunning
        val elapsed = sessionElapsed
        val economy = FuelEstimateStore(context).mpg
        val fuel = economy?.takeIf { it > 0 }?.let { "${AppUnits.get(context).volume(sessionMeters / 1609.344 / it)} used" } ?: "Fuel used unavailable"
        val summary = "${sessionText()}\n$fuel"
        val edit = prefs.edit().putLong("sessionElapsed", elapsed).putBoolean("running", false).putString("status", message)
        if (wasRunning) {
            val recordedStart = prefs.getLong("sessionStartedAt", 0)
            val startedAt = recordedStart.takeIf { it > 0 } ?: (System.currentTimeMillis() - elapsed).coerceAtLeast(0)
            val startLabel = if (recordedStart > 0) "Started" else "Started about"
            edit.putString("lastSessionStartLabel", "$startLabel ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(startedAt))}")
                .putString("lastSessionMeters", sessionMeters.toString()).putLong("lastSessionElapsed", elapsed)
                .putString("lastSessionEstimatedMeters", sessionEstimatedMeters.toString())
            if (economy != null && economy > 0) edit.putString("lastSessionGallons", (sessionMeters / 1609.344 / economy).toString())
            else edit.remove("lastSessionGallons")
            val event = org.json.JSONObject().put("type", "ride").put("startedAt", startedAt)
                .put("endedAt", System.currentTimeMillis()).put("meters", sessionMeters).put("elapsedMillis", elapsed).put("estimatedMeters", sessionEstimatedMeters)
            if (economy != null && economy > 0) event.put("gallons", sessionMeters / 1609.344 / economy)
            RideHistory(context).add(event)
            edit.putString("lastSummary", "$startLabel ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(startedAt))}\n$summary\nGPS distance only; idling and missed miles excluded")
        }
        edit.apply()
        if (wasRunning) FuelSummaryNotice.sessionStopped(context, summary)
    }
    fun status(message: String) { prefs.edit().putString("status", message).apply() }
    fun step(step: RideStep) {
        if (!step.accepted || !requestedRunning) return
        prefs.edit().putString("meters", (meters + step.meters).toString()).putBoolean("gap", gap || step.gap)
            .putString("sessionMeters", (sessionMeters + step.meters).toString())
            .putString("estimatedMeters", (estimatedMeters + step.estimatedMeters).toString())
            .putString("sessionEstimatedMeters", (sessionEstimatedMeters + step.estimatedMeters).toString())
            .putLong("lastFix", System.currentTimeMillis()).putString("status", "GPS tracking active").apply()
    }
    fun markGap() { prefs.edit().putBoolean("gap", true).apply() }
    fun baseline() { baseline(prefs.edit()).apply() }
    internal fun baseline(edit: android.content.SharedPreferences.Editor): android.content.SharedPreferences.Editor {
        return edit.putString("meters", "0")
            .putString("estimatedMeters", "0")
            .putBoolean("gap", false).putString("manualMiles", "0").putLong("lastFix", 0).putString("status", "Fuel baseline saved; tracking stopped")
    }
    fun correctMiles(miles: Double) { correctMiles(prefs.edit(), miles).apply() }
    internal fun correctMiles(edit: android.content.SharedPreferences.Editor, miles: Double): android.content.SharedPreferences.Editor =
        edit.putString("meters", (miles * 1609.344).toString()).putString("estimatedMeters", "0").putString("manualMiles", "0").putBoolean("gap", false)
    fun replaceManualMiles(value: Double) {
        check(!running)
        val corrected = RideSafety.replaceManualMeters(meters, manualMiles, value)
        prefs.edit().putString("meters", corrected.toString()).putString("manualMiles", value.toString()).apply()
    }
    internal fun replaceManualMiles(edit: android.content.SharedPreferences.Editor, value: Double) {
        check(!running)
        val corrected = RideSafety.replaceManualMeters(meters, manualMiles, value)
        edit.putString("meters", corrected.toString()).putString("manualMiles", value.toString())
    }
    fun addMissedMiles(total: Double, added: Double) {
        val edit = prefs.edit()
        addMissedMiles(edit, total, added)
        edit.apply()
    }
    internal fun addMissedMiles(edit: android.content.SharedPreferences.Editor, total: Double, added: Double) {
        edit.putString("meters", (total * 1609.344).toString())
            .putString("manualMiles", (manualMiles + added).toString())
            .putString("status", "Missed miles added; tracking stopped")
    }
    fun distanceText() = "${AppUnits.get(context).distance(meters / 1609.344, 2)} tracked since fuel baseline"
    fun qualityText(): String = when {
        estimatedMeters > 0 -> "Includes ${AppUnits.get(context).distance(estimatedMeters / 1609.344, 2)} estimated across GPS gaps; road distance may be longer and other gaps may remain"
        gap -> "Tracking gaps: distance may be missing; fuel estimate may be too high"
        requestedRunning && !running -> "Tracking interrupted; reopen phone app and restart"
        running && (lastGpsSignal == 0L || System.currentTimeMillis() - lastGpsSignal !in 0L..30_000L) -> "Waiting for reliable GPS; current distance incomplete"
        else -> "Estimate only • Does not account for idling or changing economy"
    }
}
