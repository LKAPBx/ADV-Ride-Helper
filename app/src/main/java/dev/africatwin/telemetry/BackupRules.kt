package dev.africatwin.telemetry

internal object BackupRules {
    val groups = mapOf(
        "fuel_estimate" to "capacity filledCapacity baselineOdometer currentOdometer mpg calibratedAt",
        "ride_tracking" to "meters manualMiles estimatedMeters gap lastSummary lastSessionStartLabel lastSessionMeters lastSessionEstimatedMeters lastSessionElapsed lastSessionGallons",
        "tpms" to "front rear", "tpms_alerts" to "front rear background",
        "dashboard_crops" to "box0 box1 configured", "appearance" to "mode", "dashboard" to "map",
        "honda_auto_tracking" to "deviceName bluetooth", "ride_disconnect" to "enabled thresholdMph",
        "fuel_notice" to "enabled minutes lowEnabled lowMiles stopSummary",
        "station_search_options" to "autoPercent",
        "units" to "Distance|Speed|Fuel volume|Fuel economy|Tire pressure|Temperature|Photo MPG convention"
    ).mapValues { (name, keys) -> keys.split(if (name == "units") '|' else ' ').toSet() }
    fun validate(group: String, key: String, value: Any) {
        require(key in groups[group].orEmpty()) { "Unsupported backup setting" }
        val booleans = setOf("gap", "background", "configured", "map", "bluetooth", "enabled", "lowEnabled", "stopSummary")
        val ints = mapOf("thresholdMph" to 0..100, "autoPercent" to 1..100, "lowMiles" to 1..1000, "minutes" to 1..60)
        when {
            key in booleans -> require(value is Boolean) { "Invalid switch: $key" }
            key in ints -> require(value is Number && value.toDouble() == value.toInt().toDouble() && value.toInt() in ints.getValue(key)) { "Invalid number: $key" }
            group == "units" -> require(value is String && UnitChoices.valid(key, value)) { "Unsupported units" }
            group == "tpms" -> require(value is String && (value.isEmpty() || Regex("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}").matches(value))) { "Invalid sensor assignment" }
            group == "appearance" -> require(value in listOf("system", "light", "dark")) { "Invalid appearance" }
            key.startsWith("box") -> {
                require(value is String)
                val v = value.split(',').map { it.toFloatOrNull() ?: error("Invalid crop coordinate") }
                require(v.size == 4 && v.all { it.isFinite() && it in 0f..1f } && v[2] - v[0] >= .04f && v[3] - v[1] >= .04f) { "Invalid crop rectangle" }
            }
            key in setOf("lastSummary", "lastSessionStartLabel", "deviceName") -> require(value is String && value.length <= if (key == "deviceName") 100 else 20_000) { "Invalid text" }
            key == "lastSessionElapsed" -> require(value is Number && value.toDouble() in 0.0..9e15) { "Invalid session duration" }
            else -> {
                require(value is String) { "Invalid measurement: $key" }
                val number = value.toDoubleOrNull() ?: error("Invalid measurement: $key")
                val range = when {
                    group == "tpms_alerts" -> 0.0..150.0
                    key in setOf("capacity", "filledCapacity") -> .1..20.0
                    key == "mpg" -> 1.0..200.0
                    key in setOf("baselineOdometer", "currentOdometer", "manualMiles", "lastSessionGallons") -> 0.0..1_000_000.0
                    key in setOf("meters", "lastSessionMeters", "estimatedMeters", "lastSessionEstimatedMeters") -> 0.0..1_609_344_000.0
                    else -> 0.0..9e15
                }
                require(number.isFinite() && number in range) { "Measurement out of range: $key" }
            }
        }
    }
}
