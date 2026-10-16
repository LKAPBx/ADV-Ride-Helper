package dev.africatwin.telemetry

import java.util.Locale

/** Stored ride/fuel values stay in miles, US gallons, US MPG and PSI. Preferences only convert at boundaries. */
internal data class UnitFormats(
    val distanceUnit: String = "mi", val speedUnit: String = "mph", val volumeUnit: String = "USG",
    val economyUnit: String = "US MPG", val pressureUnit: String = "PSI", val temperatureUnit: String = "°F"
) {
    fun distanceValue(miles: Double) = if (distanceUnit == "km") miles * 1.609344 else miles
    fun distanceInput(value: Double) = if (distanceUnit == "km") value / 1.609344 else value
    fun speedValue(mph: Double) = if (speedUnit == "km/h") mph * 1.609344 else mph
    fun speedInput(value: Double) = if (speedUnit == "km/h") value / 1.609344 else value
    fun volumeValue(gallons: Double) = when (volumeUnit) { "L" -> gallons * 3.785411784; "Imp gal" -> gallons / 1.2009499255; else -> gallons }
    fun volumeInput(value: Double) = when (volumeUnit) { "L" -> value / 3.785411784; "Imp gal" -> value * 1.2009499255; else -> value }
    fun economyValue(mpg: Double) = when (economyUnit) { "Imp MPG" -> mpg * 1.2009499255; "L/100 km" -> 235.214583 / mpg; "km/L" -> mpg * 0.4251437074; else -> mpg }
    fun economyInput(value: Double) = when (economyUnit) { "Imp MPG" -> value / 1.2009499255; "L/100 km" -> 235.214583 / value; "km/L" -> value / 0.4251437074; else -> value }
    fun pressureValue(psi: Double) = when (pressureUnit) { "kPa" -> psi * 6.894757293; "bar" -> psi * 0.06894757293; else -> psi }
    fun pressureInput(value: Double) = when (pressureUnit) { "kPa" -> value / 6.894757293; "bar" -> value / 0.06894757293; else -> value }
    fun number(value: Double, decimals: Int) = String.format(Locale.US, "%.${decimals}f", value)
    fun distance(miles: Double, decimals: Int = 1) = "${number(distanceValue(miles), decimals)} $distanceUnit"
    fun shortDistance(meters: Double) = if (distanceUnit == "km") "${number(meters, 2)} m" else "${number(meters / 0.3048, 2)} ft"
    fun speed(mph: Double) = "${number(speedValue(mph), 0)} $speedUnit"
    fun volume(gallons: Double, decimals: Int = 2) = "${number(volumeValue(gallons), decimals)} $volumeUnit"
    fun economy(mpg: Double) = "${number(economyValue(mpg), 1)} $economyUnit"
    fun pressure(psi: Double) = "${number(pressureValue(psi), if (pressureUnit == "bar") 2 else 1)} $pressureUnit"
    fun temperature(celsius: Int?) = celsius?.let { "${number(if (temperatureUnit == "°C") it.toDouble() else it * 1.8 + 32, 0)}$temperatureUnit" } ?: "—$temperatureUnit"
    fun session(meters: Double, elapsed: Long): String {
        val seconds = elapsed.coerceAtLeast(0) / 1000
        return distance(meters.coerceAtLeast(0.0) / 1609.344, 2) + String.format(Locale.US, " • %02d:%02d:%02d elapsed", seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
    fun legacySummary(text: String): String {
        var result = Regex("(\\d+(?:[.,]\\d+)?) mi\\b").replace(text) { distance(it.groupValues[1].replace(',', '.').toDouble(), 2) }
        result = Regex("(\\d+(?:[.,]\\d+)?) (?:USG|US gal(?: estimated)?)(?: used)?").replace(result) {
            volume(it.groupValues[1].replace(',', '.').toDouble()) + " used"
        }
        return result
    }
}

internal object UnitChoices {
    val values = linkedMapOf(
        "Distance" to listOf("mi", "km"), "Speed" to listOf("mph", "km/h"),
        "Fuel volume" to listOf("USG", "L", "Imp gal"), "Fuel economy" to listOf("US MPG", "Imp MPG", "L/100 km", "km/L"),
        "Tire pressure" to listOf("PSI", "kPa", "bar"), "Temperature" to listOf("°F", "°C"),
        "Photo MPG convention" to listOf("US MPG", "Imp MPG"))
    fun valid(key: String, value: String) = value in values[key].orEmpty()
}
