package dev.africatwin.telemetry

import android.content.Context
import android.content.Intent
import org.json.*

internal object RideBackup {
    // Explicit allowlist excludes running flags, permissions, sensor readings, alerts, location caches and credentials.
    private val groups = BackupRules.groups
    fun encode(c: Context): String {
        val settings = JSONObject()
        groups.forEach { (name, keys) ->
            val values = JSONObject()
            val source = if (name == "fuel_estimate" || name == "ride_tracking") FuelRideState.preferences(c)
                else c.getSharedPreferences(name, 0)
            source.all.filterKeys { it in keys }.forEach { (key, value) -> values.put(key, value) }
            settings.put(name, values)
        }
        val events = JSONArray(); RideHistory(c).events().forEach(events::put)
        return JSONObject().put("schema", 1).put("app", "dev.africatwin.telemetry").put("settings", settings)
            .put("history", events).toString(2)
    }
    fun parse(text: String): JSONObject {
        require(text.toByteArray().size <= 2_000_000) { "Backup is too large" }
        val root = JSONObject(text)
        require(root.getInt("schema") == 1 && root.getString("app") == "dev.africatwin.telemetry") { "Unsupported backup format" }
        val settings = root.getJSONObject("settings")
        require(settings.keys().asSequence().toSet() == groups.keys) { "Backup settings are incomplete or unsupported" }
        groups.forEach { (name, keys) ->
            val values = settings.getJSONObject(name)
            require(values.keys().asSequence().all { it in keys }) { "Unsupported setting in $name" }
            values.keys().forEach { key -> BackupRules.validate(name, key, values.get(key)) }
        }
        val fuel = settings.getJSONObject("fuel_estimate")
        if (fuel.has("baselineOdometer") && fuel.has("currentOdometer"))
            require(fuel.getString("currentOdometer").toDouble() >= fuel.getString("baselineOdometer").toDouble()) { "Odometer is below its fill-up baseline" }
        val events = root.getJSONArray("history")
        require(events.length() <= 1000) { "Too many history entries" }
        val ids = mutableSetOf<String>()
        for (i in 0 until events.length()) {
            val e = events.getJSONObject(i)
            require(e.optString("type") in listOf("ride", "fill", "update")) { "Invalid history type" }
            require(e.optString("id").length in 1..100 && ids.add(e.getString("id"))) { "Invalid/duplicate history ID" }
            e.keys().forEach { key ->
                val value = e.get(key)
                require(key in setOf("id", "type", "recordedAt", "startedAt", "endedAt", "meters", "estimatedMeters", "elapsedMillis", "gallons", "tankGallons", "mpg", "odometerMiles", "milesSinceFill", "undone")) { "Unsupported history field" }
                when (key) {
                    "id", "type" -> require(value is String)
                    "undone" -> require(value is Boolean)
                    else -> require(value is Number && value.toDouble().isFinite() && value.toDouble() in 0.0..9e15) { "Invalid history measurement" }
                }
            }
            require(e.has("recordedAt")) { "History date missing" }
            require(e.optDouble("estimatedMeters", 0.0) <= e.optDouble("meters", 0.0)) { "Estimated distance exceeds total" }
        }
        val ride = settings.getJSONObject("ride_tracking")
        for ((estimated, total) in listOf("estimatedMeters" to "meters", "lastSessionEstimatedMeters" to "lastSessionMeters"))
            require(ride.optString(estimated, "0").toDouble() <= ride.optString(total, "0").toDouble()) { "Estimated distance exceeds total" }
        return root
    }
    fun preview(root: JSONObject) = "Restore ${root.getJSONObject("settings").length()} settings groups and ${root.getJSONArray("history").length()} history entries?\n\nThis replaces saved settings, fuel baseline, sensor assignments and history. Live readings, Android permissions and running sessions are not restored."
    fun restore(c: Context, root: JSONObject) {
        require(!RideStore(c).requestedRunning && !c.getSharedPreferences("ride_edit", 0).getBoolean("editing", false)) { "Stop tracking and finish editing readings before restoring." }
        val checked = parse(root.toString()) // Validate everything before writing any preference.
        val previous = parse(encode(c))
        TpmsRecovery.disarm(c); TpmsMonitor.reset(c, "Settings restored")
        c.stopService(Intent(c, TpmsBackgroundService::class.java))
        fun apply(data: JSONObject) {
            val combined = FuelRideState.preferences(c)
            val combinedEdit = combined.edit()
            for (name in listOf("fuel_estimate", "ride_tracking")) {
                val keys = groups.getValue(name)
                keys.forEach(combinedEdit::remove)
                val values = data.getJSONObject("settings").getJSONObject(name)
                values.keys().forEach { key -> when (val value = values.get(key)) {
                    is Boolean -> combinedEdit.putBoolean(key, value)
                    is Number -> if (key == "lastSessionElapsed") combinedEdit.putLong(key, value.toLong()) else combinedEdit.putInt(key, value.toInt())
                    is String -> combinedEdit.putString(key, value)
                } }
            }
            check(combinedEdit.commit()) { "Could not save restored fuel and ride state" }
            groups.forEach { (name, keys) ->
                if (name == "fuel_estimate" || name == "ride_tracking") return@forEach
                val edit = c.getSharedPreferences(name, 0).edit()
                keys.forEach(edit::remove)
                val values = data.getJSONObject("settings").getJSONObject(name)
                values.keys().forEach { key -> when (val value = values.get(key)) {
                    is Boolean -> edit.putBoolean(key, value)
                    is Number -> if (key == "lastSessionElapsed") edit.putLong(key, value.toLong()) else edit.putInt(key, value.toInt())
                    is String -> edit.putString(key, value)
                } }
                check(edit.commit()) { "Could not save restored settings" }
            }
            check(c.getSharedPreferences("ride_history", 0).edit().putString("events", data.getJSONArray("history").toString()).commit())
        }
        try { apply(checked) } catch (e: Exception) { runCatching { apply(previous) }; throw e }
        val sensorPrefs = c.getSharedPreferences("tpms", 0)
        val edit = sensorPrefs.edit(); sensorPrefs.all.keys.filter { it.startsWith("reading:") || it.startsWith("staleAfter:") }.forEach(edit::remove); edit.apply()
        c.getSharedPreferences("fuel_undo", 0).edit().clear().apply()
        c.getSharedPreferences("fuel_notice", 0).edit().putBoolean("lowAlerted", false).apply()
        TpmsAlerts.assignmentsChanged(c)
        RideDisconnect.clear(c)
        TpmsRecovery.arm(c, true)
    }
}
