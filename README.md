# ADV Ride Helper

Current source: **0.135 (version code 135)**. This Android app estimates fuel and range from confirmed dashboard readings and recorded distance. It does not claim access to Honda fuel, odometer, or speed telemetry.

## Phone app

Use **Fill up** after a full tank to save the odometer baseline. Use **Update dashboard reading** for a photo or manual reading; review and correct the values before saving. You can update MPG or the cumulative manually added distance separately. The app estimates fuel and range from tank capacity, distance since fill-up, and average economy. It cannot account for idling, changing riding conditions, reserve fuel, or partial fills.

Start GPS tracking before riding. Tracking records phone or Android Auto host locations when available, and may estimate a straight-line gap after a lost fix when the implied average speed is plausible. It cannot reconstruct the route and can undercount road distance. A foreground notification shows the running session and Stop control. Automatic start/stop depends on Android Auto and the configured head-unit Bluetooth name, plus Android background-start and location permission rules.

The app can scan FOBO Bike 2 TPMS advertisements, show pressure and temperature, assign sensors, and notify for configured low/zero pressure. The displayed battery percentage is inferred from a user-proposed packet mapping and has not been verified against changing battery levels; treat it as an estimate.

## Android Auto

The projected app uses Android for Cars templates. Its main view shows estimated fuel/range, current-session details, heading/GPS status, and tire readings when TPMS is enabled. Find gas searches OpenStreetMap data and can open a selected station in a navigation app. Distances are straight-line estimates; station hours, fuel grade, prices, and road reachability are not verified. The host controls template layout and map rendering.

“Car-GPS” identifies a location source reported by the car app API; it does not prove that the motorcycle’s receiver supplied it. Honda fuel, odometer, and speed telemetry are not claimed.

## Station search and privacy

Search uses the public [Overpass API](https://overpass-api.de/api/interpreter) with OpenStreetMap fuel-station data. It expands from 15 to 30 to 60 miles, stopping after three results or the search limit. The displayed units follow the app’s distance setting. Results depend on community map coverage and public service availability. A search sends a rounded location to Overpass. See [FUEL_SEARCH_SETUP.md](FUEL_SEARCH_SETUP.md).

GPS and BLE sensor readings are stored locally for app operation. Diagnostic reports can include device identifiers and sensor data; review a report before sharing it. Fuel/ride state is migrated locally from earlier preference files. JSON backup omits live location, active sessions, permissions, and live tire readings; see **Settings → History, backup & monitoring**.

## Build and install

Open this folder in Android Studio, sync Gradle, then use **Build → Generate Signed Bundle / APK** with the existing Play upload key. Keep the application ID `dev.africatwin.telemetry`. Install updates through the Play testing track when validating Android Auto launcher visibility. The internal test link is [ADV Ride Helper](https://play.google.com/apps/internaltest/4701735409868216185).

The current project pins Car App Library **1.8.0-rc01** because its release notes include a security fix; 1.7.0 is the stable release. Check the [official release notes](https://developer.android.com/jetpack/androidx/releases/car-app) before changing versions. The release candidate is intentional, not an accidental version drift.

## Validation

Run the Gradle unit tests with `gradlew.bat :app:testDebugUnitTest` on Windows or `./gradlew :app:testDebugUnitTest` elsewhere. The standalone installed-library contract check in `test/AutoTemplateCheck.java` is an additional local check. These do not replace a full Android Studio resource build or phone, DHU, and motorcycle testing. Check [VALIDATION.md](VALIDATION.md) for current verification limits and history.

Older setup notes are archived in [GPS_RIDE_SETUP.md](GPS_RIDE_SETUP.md), [PHOTO_FUEL_SETUP.md](PHOTO_FUEL_SETUP.md), and [HISTORY.md](HISTORY.md); follow this README for current behavior.
