package dev.africatwin.telemetry

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class RideHistory(private val context: Context) {
    private val prefs = context.getSharedPreferences("ride_history", Context.MODE_PRIVATE)
    fun events(): List<JSONObject> = runCatching {
        val array = JSONArray(prefs.getString("events", "[]"))
        (0 until array.length()).map { array.getJSONObject(it) }
    }.getOrDefault(emptyList())
    fun add(event: JSONObject) {
        event.put("id", java.util.UUID.randomUUID().toString()).put("recordedAt", System.currentTimeMillis())
        val array = JSONArray(); (events().takeLast(999) + event).forEach(array::put)
        prefs.edit().putString("events", array.toString()).apply()
    }
    fun markUndone(id: String?) {
        if (id == null) return
        val array = JSONArray(); events().forEach { if (it.optString("id") == id) it.put("undone", true); array.put(it) }
        prefs.edit().putString("events", array.toString()).apply()
    }
    fun text(): String {
        val units = AppUnits.get(context)
        return events().asReversed().joinToString("\n\n") { e ->
            val time = e.optLong("startedAt", e.optLong("recordedAt"))
            val date = java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time))
            val heading = if (e.optString("type") == "ride") "Ride • $date" else if (e.optString("type") == "fill") "Fill-up • $date" else "Dashboard update • $date"
            heading + (if (e.optBoolean("undone")) " • UNDONE" else "") + "\n" +
                if (e.optString("type") == "ride") units.session(e.optDouble("meters"), e.optLong("elapsedMillis")) + "\n" +
                    (if (e.has("gallons")) units.volume(e.getDouble("gallons")) + " used" else "Fuel used unavailable") +
                    (if (e.optDouble("estimatedMeters", 0.0) > 0) "\nIncludes ${units.distance(e.getDouble("estimatedMeters") / 1609.344, 2)} estimated across GPS gaps" else "")
                else listOfNotNull(e.optDouble("odometerMiles", Double.NaN).takeIf { it.isFinite() }?.let { "Odometer: ${units.distance(it)}" },
                    e.optDouble("milesSinceFill", Double.NaN).takeIf { it.isFinite() }?.let { "Since fill-up: ${units.distance(it)}" },
                    e.optDouble("mpg", Double.NaN).takeIf { it.isFinite() }?.let { units.economy(it) },
                    e.optDouble("tankGallons", Double.NaN).takeIf { it.isFinite() }?.let { "Full-tank baseline: ${units.volume(it)}" }).joinToString(" • ")
        }.ifEmpty { "No history yet. New completed rides and confirmed fuel readings will appear here." }
    }
    fun csv(): String {
        val fields = listOf("id", "type", "recordedAt", "startedAt", "endedAt", "meters", "estimatedMeters", "elapsedMillis", "gallons", "tankGallons", "mpg", "odometerMiles", "milesSinceFill", "undone")
        val labels = mapOf("recordedAt" to "recordedAt_epoch_ms", "startedAt" to "startedAt_epoch_ms", "endedAt" to "endedAt_epoch_ms",
            "gallons" to "fuelUsed_US_gallons", "tankGallons" to "fullTankBaseline_US_gallons", "mpg" to "economy_US_MPG")
        return fields.joinToString(",") { labels[it] ?: it } + "\r\n" + events().joinToString("\r\n") { e -> fields.joinToString(",") { key -> RideCsv.cell(e.optString(key, "")) } }
    }
}
