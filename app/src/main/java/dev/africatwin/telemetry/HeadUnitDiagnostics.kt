package dev.africatwin.telemetry

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.car.app.CarContext
import androidx.car.app.HostInfo
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.CarValue
import org.json.JSONArray
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** Local, bounded session history. Host software versions are not physical head-unit firmware. */
internal class HeadUnitDiagnostics(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("head_unit_diagnostics", Context.MODE_PRIVATE)
    private fun history(): JSONArray = try { JSONArray(prefs.getString("history", "[]")) } catch (_: RuntimeException) { JSONArray() }
    private fun save(records: JSONArray) { prefs.edit().putString("history", records.toString()).apply() }
    private fun update(id: String, message: String) {
        val records = history()
        for (i in 0 until records.length()) {
            val record = records.getJSONObject(i)
            if (record.optString("id") == id) {
                record.put("vehicle", message); save(records)
                Log.i("ATHeadUnit", message)
                return
            }
        }
    }
    fun capture(car: CarContext, host: HostInfo?, onManufacturer: (String?, Boolean) -> String = { _, _ -> "" }) {
        val id = UUID.randomUUID().toString()
        val hostVersion = try {
            val info = car.packageManager.getPackageInfo(host?.packageName ?: "", 0)
            "${info.versionName ?: "unknown"} (code ${info.longVersionCode})"
        } catch (_: Exception) { "Unavailable to this app" }
        val record = JSONObject().put("id", id).put("time", System.currentTimeMillis())
            .put("app", "${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})")
            .put("host", host?.packageName ?: "Unavailable")
            .put("hostVersion", hostVersion).put("api", car.carAppApiLevel)
            .put("phone", "Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT}")
            .put("vehicle", "Waiting for vehicle identity response")
        val records = JSONArray().put(record)
        val old = history()
        for (i in 0 until minOf(old.length(), 19)) records.put(old.getJSONObject(i))
        save(records)
        Log.i("ATHeadUnit", "Session $id: host=${host?.packageName}, hostVersion=$hostVersion, Car App API=${car.carAppApiLevel}")
        if (car.carAppApiLevel < 3) { update(id, "Vehicle identity query requires Car App API 3"); return }
        val handler = Handler(Looper.getMainLooper())
        var answered = false
        val timeout = Runnable { if (!answered) update(id, "No vehicle identity response within 15 seconds; a late response may still arrive") }
        handler.postDelayed(timeout, 15_000)
        try {
            car.getCarService(CarHardwareManager::class.java).carInfo.fetchModel(car.mainExecutor) { model ->
                answered = true; handler.removeCallbacks(timeout)
                val autoResult = onManufacturer(model.manufacturer.value, model.manufacturer.status == CarValue.STATUS_SUCCESS)
                update(id, listOf(field("Manufacturer", model.manufacturer), field("Model", model.name), field("Year", model.year), autoResult).filter { it.isNotEmpty() }.joinToString("\n"))
            }
        } catch (e: RuntimeException) {
            answered = true; handler.removeCallbacks(timeout)
            update(id, "Vehicle identity query failed: ${e.javaClass.simpleName}")
        }
    }
    private fun field(label: String, value: CarValue<*>): String {
        val status = when (value.status) {
            CarValue.STATUS_SUCCESS -> "SUCCESS"
            CarValue.STATUS_UNKNOWN -> "UNKNOWN"
            CarValue.STATUS_UNAVAILABLE -> "UNAVAILABLE"
            CarValue.STATUS_UNIMPLEMENTED -> "UNIMPLEMENTED"
            else -> "status ${value.status}"
        }
        val usable = if (value.status == CarValue.STATUS_SUCCESS) value.value?.toString()?.take(200) ?: "No value" else "Not usable"
        return "$label: $usable [$status; timestamp ${value.timestampMillis}]"
    }
    fun report(): String {
        val records = history()
        if (records.length() == 0) return "No Android Auto startup recorded yet. Connect to the bike and open this app on Android Auto, then check here."
        return "Saved locally: last ${records.length()} Android Auto app starts.\nHost version describes the phone's host software, not Honda firmware. Vehicle identity depends on what the host supplies; this is not a unique head-unit ID.\n\n" +
            (0 until records.length()).joinToString("\n\n────────\n\n") { index ->
                val record = records.getJSONObject(index)
                "${DateFormat.getDateTimeInstance().format(Date(record.getLong("time")))}\n" +
                    "App: ${record.optString("app")}\nHost package: ${record.optString("host")}\n" +
                    "Host software version: ${record.optString("hostVersion")}\nCar App API: ${record.optInt("api")}\n" +
                    "Phone OS: ${record.optString("phone")}\n${record.optString("vehicle")}"
            }
    }
}
