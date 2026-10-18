package dev.africatwin.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainRulesTest {
    @Test fun fuelAndRideMigrationKeepsOldValuesAndPrefersExistingNewValues() {
        val migrated = FuelRideState.mergeLegacy(
            mapOf("mpg" to "43.6", "baselineOdometer" to "187.0"),
            mapOf("meters" to "1609.344", "gap" to true),
            mapOf("mpg" to "44.0", "_storageVersion" to 99, "unsupported" to Any())
        )
        assertEquals("44.0", migrated["mpg"])
        assertEquals("187.0", migrated["baselineOdometer"])
        assertEquals("1609.344", migrated["meters"])
        assertEquals(true, migrated["gap"])
        assertEquals(1, migrated["_storageVersion"])
        assertFalse(migrated.containsKey("unsupported"))
    }

    @Test fun fuelMathAndManualDistanceReplacementRemainConsistent() {
        assertEquals(2.5, EstimatedFuel.gallons(5.0, 100.0, 40.0)!!, 0.000001)
        val corrected = RideSafety.replaceManualMeters(150.0 * 1609.344, 100.0, 20.0)
        assertEquals(70.0 * 1609.344, corrected, 0.001)
        assertEquals(2.0, 5.0 - corrected / 1609.344 / 40.0, 0.000001)
    }

    @Test fun gpsGapEstimateIsBoundedAndMarked() {
        val tracker = RideDistance()
        assertTrue(tracker.add(RideFix(32.0, -96.0, 5.0, 1_000L, "gps"), 1_000L).accepted)
        val moved = tracker.add(RideFix(32.0, -95.999, 5.0, 35_000L, "gps"), 35_000L)
        assertTrue(moved.accepted)
        assertTrue(moved.gap)
        assertTrue(moved.estimatedMeters > 0)
        val teleport = tracker.add(RideFix(33.0, -95.0, 5.0, 36_000L, "gps"), 36_000L)
        assertTrue(teleport.gap)
        assertEquals(0.0, teleport.meters, 0.0)
    }

    @Test fun stationRadiusExpansionStopsAtThreeOrSixtyMiles() {
        val initial = NearestStations.INITIAL_SEARCH_RADIUS
        val second = NearestStations.nextSearchRadius(2, initial)!!
        val third = NearestStations.nextSearchRadius(1, second)!!
        assertEquals(30.0 * 1609.344, second, 0.001)
        assertEquals(60.0 * 1609.344, third, 0.001)
        assertTrue(NearestStations.nextSearchRadius(3, initial) == null)
        assertTrue(NearestStations.nextSearchRadius(0, third) == null)
    }

    @Test fun tireBatteryBandsRejectUnverifiedOutOfRangeValues() {
        assertEquals(0, TireBatteryLevel.bars(0) ?: -1)
        assertEquals(1, TireBatteryLevel.bars(1) ?: -1)
        assertEquals(2, TireBatteryLevel.bars(26) ?: -1)
        assertEquals(4, TireBatteryLevel.bars(100) ?: -1)
        assertTrue(TireBatteryLevel.bars(101) == null)
        assertTrue(TireBatteryLevel.bars(null) == null)
    }
}
