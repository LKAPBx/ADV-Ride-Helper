package dev.africatwin.telemetry

internal data class DashboardReadings(val odometerMiles: Double?, val averageMpg: Double?, val notes: String,
    val tripMiles: Double? = null, val tripMeter: String? = null)

internal object DashboardTextParser {
    private val number = Regex("(?<![A-Z0-9])(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?(?![A-Z0-9])")
    fun parse(text: String, photoEconomyUnit: String = "US MPG"): DashboardReadings {
        // Normalize only explicit value/unit pairs. Never interpret an unlabelled number as metric.
        var normalized = text.uppercase()
        fun normalizeEconomy(pattern: String, unit: String) {
            normalized = Regex(pattern).replace(normalized) {
                val value = it.groupValues[1].toDoubleOrNull()
                if (value == null || value <= 0) it.value else "${UnitFormats(economyUnit = unit).economyInput(value)} USMPG"
            }
        }
        normalizeEconomy("(\\d+(?:\\.\\d+)?)\\s*L\\s*/\\s*100\\s*KM\\b", "L/100 km")
        normalizeEconomy("(\\d+(?:\\.\\d+)?)\\s*KM\\s*/\\s*L\\b", "km/L")
        // A bare MPG label cannot distinguish UK and US gallons; use the separately configured photo unit.
        if (photoEconomyUnit == "Imp MPG" || Regex("\\b(?:UK|IMP)\\b").containsMatchIn(normalized)) {
            normalized = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:(?:UK|IMP)\\s*)?MPG\\b").replace(normalized) {
                "${UnitFormats(economyUnit = "Imp MPG").economyInput(it.groupValues[1].toDouble())} MPG"
            }.replace(Regex("\\b(?:UK|IMP)\\b"), "")
        }
        normalized = normalized.replace("USMPG", "MPG")
        normalized = Regex("(?<![\\d.,])((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?)\\s*KM\\b(?!\\s*/)").replace(normalized) {
            "${it.groupValues[1].replace(",", "").toDouble() / 1.609344} MI"
        }
        return parseCanonical(normalized).let { it.copy(notes = it.notes.replace("Use miles and US average MPG.",
            "Explicit photo units are converted; check the selected entry units. Bare MPG photos use $photoEconomyUnit.")) }
    }
    private fun parseCanonical(text: String): DashboardReadings {
        val lines = text.uppercase().lines().map { it.trim() }.filter { it.isNotEmpty() }
        val odoCandidates = mutableSetOf<Double>()
        val mpgCandidates = mutableSetOf<Double>()
        var unsupportedUnits = false
        lines.forEachIndexed { index, line ->
            // Require the odometer label; never infer it from trip distance or an arbitrary large number.
            val odometerLabel = Regex("\\b(?:ODO|ODOMETER)\\b").containsMatchIn(line)
            val totalDistanceLabel = Regex("\\bTOTAL\\b").containsMatchIn(line) && Regex("\\b(?:MI|MILE|MILES|KM)\\b").containsMatchIn("$line ${lines.getOrNull(index + 1).orEmpty()}")
            if ((odometerLabel || totalDistanceLabel) && !Regex("\\bTRIP\\b").containsMatchIn(line)) {
                val next = lines.getOrNull(index + 1).orEmpty()
                val region = if (number.containsMatchIn(line)) line else "$line $next"
                if (Regex("\\bTRIP\\b").containsMatchIn(region)) { /* A trip meter is not an odometer. */ }
                else if (Regex("\\bKM\\b").containsMatchIn(region)) unsupportedUnits = true
                else number.findAll(region).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
                    .filter { it in 0.0..1000000.0 }.forEach(odoCandidates::add)
            }
            if (Regex("\\b(?:AVG|AVERAGE)\\b").containsMatchIn(line) && !Regex("\\b(?:SPEED|MPH|INSTANT|INST)\\b").containsMatchIn(line)) {
                val next = lines.getOrNull(index + 1).orEmpty()
                val region = if (Regex("\\bMPG\\b").containsMatchIn(line) && number.containsMatchIn(line)) line else "$line $next"
                if (Regex("L\\s*/\\s*100\\s*KM|KM\\s*/\\s*L|UK|IMP").containsMatchIn(region)) unsupportedUnits = true
                else if (Regex("\\bMPG\\b").containsMatchIn(region)) number.findAll(region).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
                    .filter { it in 1.0..200.0 }.forEach(mpgCandidates::add)
            }
        }
        // Honda's labels and values can be separate OCR blocks, far apart in text order.
        // Only accept a unique MPG value with an explicit average-consumption label.
        if (!unsupportedUnits && lines.any { Regex("\\bAVG[.]?\\s*CONS[.]?\\b").containsMatchIn(it) } &&
            lines.none { Regex("\\b(?:INSTANT|INST|UK|IMP)\\b|L\\s*/\\s*100\\s*KM").containsMatchIn(it) }) {
            lines.forEach { line -> Regex("(\\d+(?:\\.\\d+)?)\\s*MPG\\b").findAll(line)
                .mapNotNull { it.groupValues[1].toDoubleOrNull() }.filter { it in 1.0..200.0 }.forEach(mpgCandidates::add) }
        }
        val odo = odoCandidates.singleOrNull()
        val mpg = mpgCandidates.singleOrNull()
        val notes = buildList {
            add("Check both numbers against the photo before confirming. Use miles and US average MPG.")
            if (odo == null) add("Odometer was missing or ambiguous; enter it manually.")
            if (mpg == null) add("Average MPG was missing or ambiguous; enter it manually.")
            if (unsupportedUnits) add("Metric/Imperial units detected; no automatic conversion was guessed.")
        }.joinToString("\n")
        val meters = Regex("\\bTRIP\\s*([AB])\\b").findAll(text.uppercase()).map { it.groupValues[1] }.toSet()
        val distances = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:MI|MILE|MILES)\\b").findAll(text.uppercase())
            .mapNotNull { it.groupValues[1].toDoubleOrNull() }.toSet()
        val trip = if (meters.size == 1 && distances.size == 1) distances.single() else null
        return DashboardReadings(odo, mpg, notes, trip, if (trip != null) "Trip ${meters.single()}" else null)
    }
    // Called only on the small, spatially anchored TOTAL-mile crop, never the entire dash.
    fun parseOdometerCrop(text: String): Double? {
        val upper = text.uppercase()
        if (Regex("\\b(?:TRIP|MPH|KM|GAL)\\b").containsMatchIn(upper)) return null
        return number.findAll(upper).mapNotNull { it.value.replace(",", "").toDoubleOrNull() }
            .filter { it in 0.0..1000000.0 }.toSet().singleOrNull()
    }
}
