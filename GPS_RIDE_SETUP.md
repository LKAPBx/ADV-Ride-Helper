> Historical guide: some controls and version-specific steps below have changed. See [the current README](README.md) for version 0.69 behavior and installation.

# GPS controls — version 0.9 (code 9)

Start / Stop GPS tracking is now a main-screen button on the phone and the first row in Android Auto (also present on the estimate details screen). Both displays read the same saved tracking state. Start on the phone requests precise location if missing. Record a full fill-up baseline first.

Auto Start is parked-only. It opens the phone app with a start request; the visible phone activity starts the location foreground service. This avoids assuming the projected car service qualifies for while-in-use location access. If opening the phone is blocked, open it manually and use its Start button. Unlock/check the phone while parked if permissions are needed. No background location permission was added. Stop works directly on either display. Fill-up and dashboard updates still require stopping tracking, then explicitly restarting it.

Before riding, wait for the status to change from Waiting for GPS to GPS tracking active. Check distance against the bike trip meter. Validate Auto handoff, Stop, permissions, screen-off recording and restarting after a dashboard update on the real phone; these have not been device-tested here.

Reference: https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start

The version 0.5 notes below describe the earlier UI and are historical.

# GPS ride tracking — version 0.5 (code 5)

This initial version measures phone GPS distance and estimates remaining fuel from a known starting amount and your measured average MPG. No bike telemetry or OBD adapter is needed. The result is always labeled estimated fuel, separate from host fuel diagnostics.

## Install and use

1. Generate a signed release AAB using your existing upload key. Upload version code 5 to Internal testing, roll it out, and update through Google Play on the phone. Do not replace the bike's Play installation using Studio Run.
2. On the phone, enter **known starting fuel in US gallons** and **your measured average MPG**, then tap **Set fuel baseline and reset tracked miles**. This clears distance from the previous baseline. No tank size or MPG is assumed for Honda.
3. Grant **precise location** and, on Android 13+, **ride notification** permission. Enable phone location. Start tracking while the phone app is visible and you are parked.
4. Tap **Start ride tracking** before riding. Wait outdoors for GPS acquisition and verify the persistent Ride GPS tracking notification. Tracking should continue with the phone screen off; this still requires on-device validation.
5. In Android Auto, tap **GPS ride / estimated fuel** to see live tracked miles, estimated gallons, tracking state and quality warnings. This screen refreshes every five seconds. The main fuel-stop screen summarizes the estimate when rendered.
6. Stop from the phone or notification after riding. Distance remains saved across sessions. Restart explicitly for the next ride; it does not automatically start after boot or process termination.
7. Compare distance with the bike's trip meter. If distance is missing, stop tracking and enter **actual total miles since the fuel baseline**, then tap **Correct total miles since baseline**. This replaces the total and clears the gap warning. Restart tracking afterward.

At the next refuel, stop tracking and establish a new known fuel baseline. This initial version does not record partial refills, learn MPG automatically, or infer tank capacity. If fuel amount is unknown, distance tracking still works but no fuel estimate is calculated.

## Calculation and filtering

Estimated gallons = max(0, starting gallons − tracked miles / MPG). A zero estimate is not a measured empty tank. Idling, wind, terrain, speed, load and changing economy are unmodeled. Missing distance makes the estimate too high; warnings must not be treated as precise fuel measurements.

GPS fixes arrive from the phone's GPS provider, requested every two seconds. The tracker rejects coordinates outside valid ranges, accuracy worse than 25 meters, fixes older than 15 seconds, duplicate/out-of-order timestamps, and jumps implying speed above 90 m/s. Small movement below an accuracy-based threshold is ignored to reduce stationary drift. Filtering can undercount short movements and corners, and cannot eliminate all GPS error.

Intervals over 30 seconds without usable fixes are marked as gaps and are not connected by an invented route. Known tracking interruption and resuming after a previous session also warn of possibly missing distance. Stationary riding/idling cannot be distinguished reliably from no movement and consumes fuel outside this formula.

Totals and configuration are stored locally using preferences. No route history or coordinates are retained by the tracker. Separately enabled station search still sends coordinates to the public Overpass API, as documented in its guide. Battery use increases during GPS tracking.

## Android and Play requirements

The service is non-exported and declares the location foreground-service type plus FOREGROUND_SERVICE and FOREGROUND_SERVICE_LOCATION permissions. It starts from an explicit phone Activity action after precise permission is granted. It requests no ACCESS_BACKGROUND_LOCATION permission. Android 13+ notifications are requested so users can see the ongoing tracking and Stop action. No automatic background/boot restart or battery-exemption request is implemented.

Play Console may ask for the location foreground-service declaration and demonstration when uploading this version. Answer based on the implemented user-started ride tracking use case and complete any review requirements shown. No publishing approval or production category eligibility is claimed.

## Validation and next tests

Debug/release source compilation passes. Executable tests cover distance ordering, drift rejection, poor accuracy, duplicate/stale timestamps, implausible jumps, signal gaps and fuel arithmetic; existing diagnostics tests also pass. These checks do not validate GPS reception, notification controls, permissions, persistence during real OS termination, screen-off tracking or ride accuracy on your phone. Full Gradle build/lint and device tests remain required.

First test while stationary: start, wait for good GPS, lock the phone for several minutes, verify notification and near-zero distance, then stop from its notification. Next compare a short recorded ride against the trip meter. Also test disabling location, stopping/restarting, baseline reset, correcting total miles and reopening the app after termination. Do phone setup/corrections while parked.

References: [Location foreground service requirements](https://developer.android.com/develop/background-work/services/fgs/service-types#location), [Foreground-service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start), [Play foreground-service requirements](https://support.google.com/googleplay/android-developer/answer/13392821).


