package dev.africatwin.telemetry

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

internal class StationResultsStore(context: Context) {
    private val prefs = context.getSharedPreferences("station_results", Context.MODE_PRIVATE)
    fun freshness(): String {
        val time = prefs.getLong("time", 0)
        if (time <= 0) return "No saved search"
        val ageMinutes = ((System.currentTimeMillis() - time).coerceAtLeast(0) / 60_000)
        return "Updated ${java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(time))} • ${ageMinutes}m ago" +
            if (ageMinutes >= 5) " • Saved results; refresh when online" else ""
    }
    fun save(stations: List<FoundStation>) {
        val entries = JSONArray()
        stations.forEach { entries.put(JSONObject().put("name", it.name).put("lat", it.latitude).put("lon", it.longitude).put("meters", it.distanceMeters).put("direction", it.direction).put("bearing", it.bearingDegrees).put("source", it.searchSource)) }
        prefs.edit().putInt("schema", 2).putLong("time", System.currentTimeMillis()).putString("results", entries.toString()).apply()
    }
    fun recent(): List<FoundStation> {
        if (prefs.getInt("schema", 0) != 2) return emptyList()
        if (prefs.getLong("time", 0) <= 0 || prefs.getLong("time", 0) > System.currentTimeMillis()) return emptyList()
        return try {
            val entries = JSONArray(prefs.getString("results", "[]"))
            (0 until entries.length()).map { i -> val e = entries.getJSONObject(i)
                FoundStation(e.getString("name").replace("Mapped gas station", "Gas station"), e.getDouble("lat"), e.getDouble("lon"), e.getDouble("meters"), e.optString("direction", ""), e.optDouble("bearing", Double.NaN).takeIf { it.isFinite() }, e.optString("source", "search position")) }
        } catch (_: RuntimeException) { emptyList() }
    }
}

