package dev.africatwin.telemetry

import android.content.Context
import java.util.Locale

internal class FuelEstimateStore(private val context: Context) {
    val units get() = AppUnits.get(context)
    private val prefs = context.getSharedPreferences("fuel_estimate", Context.MODE_PRIVATE)
    private fun number(key: String) = prefs.getString(key, null)?.toDoubleOrNull()?.takeIf { it.isFinite() }
    val capacity get() = number("capacity") ?: 5.0
    val filledCapacity get() = number("filledCapacity")
    val baselineOdometer get() = number("baselineOdometer")
    val currentOdometer get() = number("currentOdometer")
    val mpg get() = number("mpg")
    fun confidenceText(): String {
        val ride = RideStore(context)
        return FuelConfidence.describe(baselineOdometer != null, mpg != null, ride.gap,
            ride.requestedRunning && !ride.running, ride.manualMiles, number("calibratedAt")?.toLong() ?: 0, System.currentTimeMillis(), ride.estimatedMeters)
    }
    fun readingText(): String = if (baselineOdometer == null) "No dashboard reading saved yet" else
        "Saved odometer: ${currentOdometer?.let { units.distance(it) } ?: "—"} • Average: ${mpg?.let { units.economy(it) } ?: "—"}\nFill-up odometer: ${units.distance(baselineOdometer!!)}"
    val miles get() = if (baselineOdometer != null) RideStore(context).meters / 1609.344 else 0.0
    val remaining get() = filledCapacity?.let { fuel -> mpg?.let { EstimatedFuel.gallons(fuel, miles, it) } }
    val range get() = remaining?.let { fuel -> mpg?.let { fuel * it } }
    fun updateTripDistance(distance: Double, economy: Double) {
        check(baselineOdometer != null && !RideStore(context).running)
        require(distance.isFinite() && distance in 0.0..1000000.0 && economy.isFinite() && economy in 1.0..200.0)
        val undo = FuelUndo(context); undo.capture()
        prefs.edit().putString("mpg", economy.toString()).apply()
        RideStore(context).correctMiles(distance)

        undo.committed()
    }
    fun updateMissedDistance(distance: Double, economy: Double) {
        check(baselineOdometer != null && !RideStore(context).running)
        require(distance.isFinite() && distance in 0.0..1000000.0 && economy.isFinite() && economy in 1.0..200.0)
        val undo = FuelUndo(context); undo.capture()
        if (distance > 0) RideStore(context).addMissedMiles(EstimatedFuel.addMiles(miles, distance) ?: error("Invalid missed miles"), distance)
        prefs.edit().putString("mpg", economy.toString()).apply()

        undo.committed()
    }
    fun setCapacity(value: Double) {
        require(value.isFinite() && value in 0.1..20.0)
        prefs.edit().putString("capacity", value.toString()).apply()
    }
    fun fillUp(odometer: Double, economy: Double) {
        require(odometer.isFinite() && odometer in 0.0..1000000.0 && economy.isFinite() && economy in 1.0..200.0)
        require(!RideStore(context).running)
        val undo = FuelUndo(context); undo.capture()
        prefs.edit().putString("baselineOdometer", odometer.toString()).putString("currentOdometer", odometer.toString())
            .putString("mpg", economy.toString()).putString("filledCapacity", capacity.toString()).apply()
        RideStore(context).baseline()
        context.getSharedPreferences("fuel_notice", Context.MODE_PRIVATE).edit().putBoolean("lowAlerted", false).apply()
        FuelSummaryNotice.clearLowRange(context)
        undo.committed("fill", odometer)
    }
    fun updateReading(odometer: Double, economy: Double) {
        val baseline = baselineOdometer ?: error("Record a fill-up first")
        require(!RideStore(context).running)
        require(odometer.isFinite() && odometer in baseline..1000000.0 && economy.isFinite() && economy in 1.0..200.0)
        val undo = FuelUndo(context); undo.capture()
        prefs.edit().putString("currentOdometer", odometer.toString()).putString("mpg", economy.toString()).apply()
        RideStore(context).correctMiles(odometer - baseline)

        undo.committed(confirmedOdometer = odometer)
    }
    fun fuelText() = remaining?.let { units.volume(it) } ?: "—"
    fun rangeText() = range?.let { units.distance(it, 0) } ?: "—"
    fun distanceText() = "${units.distance(miles)} since fill-up"
    fun detailsText() = if (baselineOdometer == null) "Record a full fill-up to start estimating"
        else "Full-tank baseline: ${filledCapacity?.let { units.volume(it, 1) } ?: "—"} • ${mpg?.let { units.economy(it) } ?: "—"}"
}

