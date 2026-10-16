package dev.africatwin.telemetry

import android.content.Context
import org.json.JSONObject

internal class FuelUndo(private val context: Context) {
    private val prefs = context.getSharedPreferences("fuel_undo", Context.MODE_PRIVATE)
    val available get() = prefs.contains("snapshot") && prefs.contains("afterMeters")
    fun capture() {
        val root = JSONObject()
        for ((name, keys) in mapOf("fuel_estimate" to listOf("baselineOdometer", "currentOdometer", "mpg", "filledCapacity", "calibratedAt"),
            "ride_tracking" to listOf("meters", "manualMiles", "estimatedMeters", "gap"))) {
            val source = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            val values = JSONObject()
            keys.forEach { values.put(it, source.all[it] ?: JSONObject.NULL) }
            root.put(name, values)
        }
        root.put("lowAlerted", context.getSharedPreferences("fuel_notice", Context.MODE_PRIVATE).getBoolean("lowAlerted", false))
        prefs.edit().putString("snapshot", root.toString()).remove("afterMeters").apply()
    }
    fun committed(kind: String = "update", confirmedOdometer: Double? = null) {
        context.getSharedPreferences("fuel_estimate", Context.MODE_PRIVATE).edit().putString("calibratedAt", System.currentTimeMillis().toString()).apply()
        val fuel = FuelEstimateStore(context)
        val event = JSONObject().put("type", kind).put("mpg", fuel.mpg).put("tankGallons", fuel.filledCapacity)
            .put("milesSinceFill", fuel.miles)
        confirmedOdometer?.let { event.put("odometerMiles", it) }
        RideHistory(context).add(event)
        prefs.edit().putString("afterMeters", RideStore(context).meters.toString()).putString("afterEstimatedMeters", RideStore(context).estimatedMeters.toString()).putString("historyId", event.getString("id")).apply()
    }
    fun restore() {
        check(available && !RideStore(context).running) { "Pause tracking before Undo" }
        val root = JSONObject(prefs.getString("snapshot", "{}")!!)
        val after = prefs.getString("afterMeters", "0")!!.toDouble()
        val current = RideStore(context)
        val oldMeters = root.getJSONObject("ride_tracking").optString("meters", "0").toDoubleOrNull() ?: 0.0
        for (name in listOf("fuel_estimate", "ride_tracking")) {
            val values = root.getJSONObject(name)
            val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit()
            values.keys().forEach { key ->
                when {
                    key == "meters" -> edit.putString(key, RideSafety.undoMeters(oldMeters, after, current.meters).toString())
                    key == "estimatedMeters" -> {
                        val beforeEstimated = values.optString(key, "0").toDoubleOrNull() ?: 0.0
                        val afterEstimated = prefs.getString("afterEstimatedMeters", "0")?.toDoubleOrNull() ?: 0.0
                        edit.putString(key, minOf(RideSafety.undoMeters(oldMeters, after, current.meters), beforeEstimated + (current.estimatedMeters - afterEstimated).coerceAtLeast(0.0)).toString())
                    }
                    key == "gap" -> edit.putBoolean(key, values.optBoolean(key, false) || current.gap)
                    values.isNull(key) -> edit.remove(key)
                    else -> edit.putString(key, values.getString(key))
                }
            }
            edit.apply()
        }
        context.getSharedPreferences("fuel_notice", Context.MODE_PRIVATE).edit().putBoolean("lowAlerted", root.optBoolean("lowAlerted", false)).apply()
        FuelSummaryNotice.clearLowRange(context)
        RideHistory(context).markUndone(prefs.getString("historyId", null))
        prefs.edit().clear().apply()
    }
}
