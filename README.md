# ADV Ride Helper

Current version: **0.97 (version code 97)**. Kotlin Android Studio prototype for estimated fuel/range, ride tracking, and nearby gas stations on the phone and projected Android Auto.

## Fuel and tracking

Configure tank capacity (default 5 US gallons), then use **Fill up** to confirm a full tank and starting dashboard reading. **Update dashboard reading** accepts a photo or manual odometer/Trip A/Trip B and average US MPG. Review recognized values before saving. Updates add the confirmed missed distance and apply MPG while retaining the fill-up baseline. Tank size changes apply to the next fill-up. Partial fills must not be recorded as a full tank.

Fuel remaining = full-tank gallons minus miles since fill-up divided by MPG, clamped to zero. Range = remaining gallons multiplied by MPG, without a reserve allowance. These are estimates, not Honda sensor readings.

The camera provides framing guides, on-device text recognition, and a segment-digit fallback. Unclear readings require manual correction. Undo restores the previous fuel update while retaining subsequent GPS distance.

Use **Start/Stop GPS** on the phone or parked Android Auto screen. Session counters reset at each tracking start. Dashboard edits pause tracking and resume afterward if it was running and no external stop occurred. Current counters show zero when stopped; completed session details remain available separately.

Automatic Honda tracking uses Android Auto connection state and a Bluetooth name containing “Honda Motorcycle”; the existing head-unit flow also recognizes “Honda_Wing”. Automatic Honda tracking is always enabled; grant the requested permissions and enable Bluetooth monitoring for background detection. Manual Stop suppresses restart for that connection. Background restrictions can prevent starting; Advanced contains start reports.

## Android Auto

Home shows fuel/range, current session counters, a larger fuel graphic, compass heading, GPS speed, and a colored tracking button. **Find gas** opens station results. The tools button opens session details, **Settings**, and **Advanced** tests/reports.

Direct parked head-unit Start requires location permissions; otherwise the app opens the phone to finish starting. Stop works directly. Heading and speed require fresh readings. Host GPS is optional; “Car-GPS” does not establish motorcycle receiver origin. Phone sensors can also supply heading.

This remains a templated POI prototype with station-search functionality. Honda fuel, speed, and odometer telemetry availability is not claimed. Standalone dashboard public publishing eligibility is not established.

## Gas stations

Search is available with precise location permission. Manual search works at any fuel level. Automatic search runs while the phone app is visible at or below a configurable threshold (default 10%), with a minimum five-minute interval.

Results show available names, straight-line distance, cardinal bearing, and an arrow relative to current heading. Tap a station to open a map. These are not road-route distances or guaranteed fuel availability/prices.

Search uses [OpenStreetMap Overpass](https://overpass-api.de/api/interpreter), first within 5 km and then 25 km if no fuel stations were found. Busy-server failures retry after a delay, up to five retries. A shared 60-second cooldown appears on the disabled search button. Advanced contains the latest search report.

Search tries current location, waits for a fresh fix, then uses the latest known real location. If none exists, it uses Hell, Michigan (42.434722, -83.985), labeled as a default. Fallback positions never add ride distance or invent speed. Cached station results show their age.

## Build and install

Open this folder in Android Studio, synchronize Gradle, and generate a signed **release Android App Bundle** using the existing upload key. Upload version code **97** to internal testing and roll it out. Update through [the internal test link](https://play.google.com/apps/internaltest/4701735409868216185). Avoid replacing the Play copy with a Studio debug installation when testing motorcycle launcher visibility.

Uses Android 37.0 SDK, AGP 9.4.0, Gradle 9.6.0, Car App Library 1.8.0-rc01, and bundled ML Kit 16.0.1. Signing secrets and local SDK settings remain outside Git.

## Cleanup and verification

Version 0.69 removes unused grid/row graphics, duplicate fuel-state helpers, and obsolete missed-mile entry code. Active update flows, diagnostics, and saved-data compatibility remain. Unchanged station results avoid rebuilding views; fallback location lookups are briefly cached. Bluetooth monitoring and periodic fuel notifications now use distinct notification IDs.

All main/debug Kotlin sources compiled against the installed SDK and cached dependencies. Executable checks passed for session resets, fallback location selection, relative station arrows, GPS speed, retry limits, fuel thresholds, errors, undo, distance filtering, fuel math, OCR parsing, and the supplied 187-mile odometer photo. Installed-library checks passed for Pane primary actions and the historical Grid action failure.

This is source/library verification, not a complete Android build or device test. Gradle/ADB daemon access is restricted here. Build in Studio and verify phone/head-unit startup, tracking, counters, and stations on the actual devices. Battery behavior still needs a real ride test.

Older notes are preserved in [HISTORY.md](HISTORY.md); older setup guides are marked historical. See [VALIDATION.md](VALIDATION.md) for validation history.

## Version 0.70: Android Auto station map

Open **Find gas**, then **Map**. Use **List** to return. The host-rendered PlaceListMapTemplate displays station markers and its current-location dot when location permission is granted. No app map API key or app map billing configuration is needed. Find gas remains available from either view and uses the existing cooldown/retry behavior. Tap a station to open navigation.

An app reference anchor uses a fresh GPS position or a labeled last-known real location. The Hell, Michigan default search center is never used as a user-position anchor. The Android Auto host controls the location dot, map availability, appearance, zoom, and layout; this feature does not prove motorcycle GPS origin. Current implementation is head-unit only; the phone retains its existing station list/map-opening behavior.

The manifest declares MAP_TEMPLATES for the existing POI category. Map station rows include required DistanceSpan and Place metadata; the status row is browsable. Local construction failures return to the list. A host-side rendering failure cannot be caught by this fallback. Full Kotlin source compilation and existing executable regressions passed. Actual host rendering/location-dot behavior still requires DHU and motorcycle testing after building in Studio.


## Version 0.71: Find gas startup fix

The station toolbar now has only one text action (Map or List), matching the installed library constraint. Tap the Find gas row to search in either view. Local station/map construction failures save an error report and return a safe screen/list. The installed-library regression reproduces the prior two-title failure and accepts the single-title configuration. Kotlin compilation and domain regression checks passed; actual host behavior still requires testing.



## Version 0.72: phone last session

The phone home session display shows the last completed session, including date/time, distance, elapsed time and estimated fuel used. It stays on the completed session during a new ride and updates after tracking stops. Before any session has completed, it shows No completed session yet. Android Auto continues to show current-session counters.



## Version 0.73: Update reading corrects missed distance

Update dashboard reading automatically uses recognized Trip A/B totals, without the old options toggle. Confirm the trip was reset at fill-up; the total replaces tracked miles to avoid double counting, and confirmed MPG recalculates fuel/range. When both trip and TOTAL are recognized, a button switches between them before saving (switching reloads recognized values). Manual entry still adds only untracked miles; enter zero for MPG only. Photos without MPG use the saved value for review. GPS pause/resume and Undo remain in place.


## Version 0.74: adjustable recognition boxes

In the camera, tap **Adjust boxes**, drag inside a box to move it, and drag the bottom-right dot to resize. Include both the labels and digits. Tap **Save boxes** before taking a photo. **Reset boxes** restores the initial shapes for review; Save commits them. Capture is disabled during adjustment.

On the captured-photo review, **Adjust recognition boxes** opens the same boxes over the actual upright image. **Save & read again** stores them and repeats recognition on that image, without saving fuel readings. Cancel discards box edits; Reset restores the default rectangles in the editor. Editing areas again reloads recognized readings, so enter manual corrections afterward.

The cyan MPG/Trip and yellow TOTAL areas are saved as fractions of the upright image. Camera guides use those same fractions. Saved areas are cropped, enlarged up to 4x (maximum 2048 pixels per side), and processed with grayscale/contrast. Recognition still runs on a photo decoded with longest side capped at 2048 pixels; these are not original-JPEG-resolution crops. Additional passes supplement missing labeled fields; existing whole-photo suggestions remain for user review. The existing automatic TOTAL/segment fallback remains. **Show recognized text** includes the saved crop pass results. No fuel state changes until Save readings.

Defaults remain preview-only until boxes are explicitly saved. Settings apply to subsequent photos, including picked/external-camera images, whose framing may differ; use the photo editor to realign. Actual preview-to-JPEG alignment and touch behavior require phone testing. Kotlin compilation/domain checks passed; no full Android build or device UI test was performed here.


## Version 0.75: phone home cleanup

The last completed session display omits the GPS-distance/idling/missed-miles note, including for previously saved sessions. Removed the duplicate Last ride summary button and home About button. About/version information remains in Settings. Kotlin source compilation passed; device UI not tested here.


## Version 0.76: photo updates match manual missed distance

Update dashboard reading now uses exactly the same additive save operation as Enter missed distance and MPG manually. Photo Trip A/B miles prefill **Untracked miles to add**, and recognized average MPG prefills the economy field. Saved MPG is offered if recognition misses it. Confirm/edit before saving; only the missed stretch should be added. A trip total containing GPS-recorded miles must be edited down to the missed miles. TOTAL odometer is never treated as miles to add; if no trip is recognized, the distance field stays blank. Enter zero for MPG only. Preview shows the resulting total, fuel and range. Fill up still uses TOTAL odometer for its baseline. Photo crop adjustment, undo and tracking pause/resume remain. This supersedes version 0.73's replacement-total behavior.


## Version 0.77: Honda automatic tracking always enabled

Removed the Automatically track Honda rides switch and all old enabled-preference gates, including for existing installations that had it disabled. Connection matching, Bluetooth-monitor setting, permission checks, fill-up requirement, editing pause and manual-Stop suppression remain. Kotlin compilation passed; reconnection/background startup still needs device testing.


## Version 0.78: two-stage background location setup

Background location setup explains the foreground and background stages separately. The foreground result continues to the background stage automatically using a dedicated request code; it does not trigger a gas search. Background location is requested separately after precise foreground access. The second dialog uses Android's localized background option label and offers App settings; denial/unavailable request routes provide Permissions → Location → Allow all the time guidance. The switch continues to reflect actual OS permission state. Android's first foreground dialog does not offer all-the-time access on Android 11+. Kotlin source compilation passed. Permission screens and automatic connection startup still require phone testing.

## Version 0.79: first-launch permission walkthrough

On first normal launch, the app presents a permission overview then requests precise foreground location, background location, Bluetooth nearby-device access, notifications and camera access in sequence, where supported and not already granted. Background access is requested separately after precise foreground permission. Already-granted permissions are skipped. The background disclosure explains closed-app ride distance recording and station-search location sharing. Not now skips the walkthrough; denied permissions are not automatically requested again. A final summary lists missing permissions and the Android location-settings path. Existing users see this setup once after updating because previous versions did not record its completion. Individual permission controls remain in Settings. Bluetooth monitoring starts after setup if allowed; head-unit Start requests wait until setup finishes.

Kotlin source compilation passed. Actual sequential permission screens, rotation restoration and background-start behavior require device testing; no signed bundle or full Android build was produced here.


## Version 0.80: clearer Settings wording

Settings are grouped into Tank, Gas stations, Notifications, Appearance, Automatic Honda rides, Permissions and More. Removed repeated connection/startup paragraphs and diagnostic manufacturer terminology. Reworded range alerts, reminder intervals, connection monitoring and location permission instructions. Tank size/next-fill behavior and immediate versus Save behavior are stated beside relevant controls. Advanced/About moved to the end. Behavior is unchanged. Kotlin compilation passed; device layout not verified here.


## Version 0.81: keep tracking after a moving disconnect

On Honda Bluetooth, Android Auto projection, or recognized Honda head-unit session loss, a running ride continues when the most recent usable GPS speed is strictly greater than 5 mph (2.2352 m/s). LiveSpeed accepts readings for up to 10 seconds. Missing/stale/invalid speed does not qualify. The keep-tracking decision is persisted for that disconnected ride so later transport/session callbacks cannot stop it based on a newer missing/slower fix. Tracking continues until manually stopped; slowing down alone does not end it. The flag/notification clear on Stop, new ride Start, or a new Android Auto projection connection.

A dedicated ongoing phone notification says Connection lost — GPS still tracking, explains the reason and opens the phone app. Android notification permission/channel settings control delivery. The existing foreground tracking notification remains. At lower/unknown speed, existing stop behavior remains; Bluetooth loss alone still does not stop an active wireless projection. Both disconnect entry points and Honda session teardown share the same handler. Phone GPS remains active after host GPS disappears; normal source-switch distance filters remain.

Kotlin compilation and domain checks passed, including strictly-above-5-mph boundary and invalid-speed cases. Actual wireless disconnect, notification delivery and GPS continuity require a device ride test. No full Android build or signed bundle produced here.

## Version 0.82: configurable disconnect continuation

Settings → Automatic Honda rides now includes Continue tracking after head-unit or Bluetooth disconnect (default on), plus Continue above speed (whole mph, 0–100; default 5). The threshold button is disabled when the option is off. Only a fresh valid GPS speed strictly above the configured threshold qualifies at disconnect. Once continued, tracking requires manual Stop while disconnected, even if speed falls, GPS becomes unavailable, or the setting is changed. Android Auto projection reconnection or a newly recognized Honda head-unit session clears continuation and its notification, restoring normal behavior for the next disconnect. Notification text uses the threshold active at disconnect. Bluetooth reconnection alone does not reset continuation. Manual Stop/new ride Start also clear it.

Kotlin compilation and executable regressions passed for custom/exact thresholds, disabled option, continued ride at zero/unknown speed and reconnect-state clearing. Actual transport reconnects, settings UI and notification delivery still require device testing.

## Version 0.83: configurable Bluetooth name

Settings → Automatic Session Start/Stop → Bluetooth name lets you change the substring used to recognize a connected device. Default is Honda Motorcycle. Matching ignores capitalization and trims outer whitespace; empty/over-100-character entries are rejected. Default in the editor restores Honda Motorcycle; Save applies it. Both ACL connect/disconnect handling and connected headset/audio-profile checks use the saved name. An active monitor rechecks connected profiles when the setting changes; reconnect if needed. The standby notification shows the saved match. Android Auto projection is still required for this Bluetooth start path. The separate Honda_Wing host-manufacturer fallback is unchanged.

Source compilation and executable regressions passed for default/custom matching, case, trimming, blank rejection and nonmatches. Actual Bluetooth profiles, reconnection and UI still require device testing. Includes the recent Settings wording changes from 0.82.


## Version 0.84: ADV Ride Helper

Renamed the installed application label, About/setup/permission text and Studio project title to ADV Ride Helper. Station request User-Agent identifies ADVRideHelper. Package/application ID remains dev.africatwin.telemetry so installed app data and Play update identity are retained. Project folder remains AfricaTwinTelemetry. Play Console store listing title is separate and must be updated there. Kotlin source compilation passed; launcher/Auto label refresh and full manifest/resource build need Studio/device verification.


Version 0.84 follow-up: phone heading uses ADV Ride Helper; session heading uses Last session; duplicate status lines are suppressed and fuel-used wording shortened. New session summaries record and display Started date/time; monotonic elapsed duration remains unchanged. Previously saved summary timestamps remain as stored. An in-progress session without a recorded wall-clock start uses stop time minus elapsed and labels it Started about. Kotlin compilation passed.



## Version 0.85: explicit Android Auto icon

The Android Auto service explicitly declares the same updated motorcycle drawable as the application; the dashboard header uses Action.APP_ICON. Includes all recent 0.84 wording, fuel graphic, session formatting and icon edits. Build/upload code 85 and update through Play. Restart Android Auto/DHU if the host retains the previous icon. Manifest XML parsed and Kotlin sources compiled; resource packaging and head-unit rendering still require Studio/device testing.


## Version 0.86: permission walkthrough recovery

Older complete/skipped setup flags get a one-time versioned reset, offering missing permissions again after updating without changing OS grants. Settings → Set up permissions restarts the sequential review at any time. Already-granted permissions still do not show system prompts. Android 11+ background location has an explanatory step after foreground location, separate from Bluetooth, notifications and camera. Its position is retained across dialog recreation. Final missing-access dialog offers Android app settings, including when prompts no longer appear after repeated denials. No permission is silently granted and there is no automatic repeated denial loop. First launch means first normal phone activity launch, not device boot or head-unit service startup.

Kotlin compilation passed; full on-device walkthrough (including denied/background/settings/rotation cases) remains to be verified. No full Android build or signed bundle generated here.

## Version 0.87: FOBO Bike 2 test and rounded phone buttons

Open phone Settings → Advanced → Scan TPMS sensors (FOBO Bike 2 test). Tap Scan while parked near your sensors. Grant Nearby devices (Android 12+) or precise location (older Android); turn Bluetooth on and, on older Android, enable phone location services. A scan lasts two minutes and stops when leaving the screen. No background TPMS monitoring, pairing, GATT connection, writes, alarms or Android Auto TPMS display is added in this version.

Candidate devices show experimental PSI/kPa and °F/°C, last broadcast age, and UNVERIFIED. After 30 seconds or when scanning stops, values say not live. Assign devices to Front/Rear; assignments persist locally and a device cannot occupy both positions. Raw packets/readings are not persisted across closing the screen. Sensors using changing addresses may require reassignment. Copy scan report includes up to 60 recent BLE packets (including other nearby devices) and up to 40 candidate devices; it is copied locally only when requested. No automatic report upload occurs.

Recognition currently accepts manufacturer ID 0x0127 or service-data UUID 0000012a-0000-1000-8000-00805f9b34fb, plus FOBO-named/assigned devices for unknown-format reporting. The experimental Type-E interpretation uses the final four bytes: temperature low seven bits minus 50 (manufacturer) or 40 (service); pressure low ten bits of the final big-endian word, in kPa. Format reference: https://github.com/bkbilly/tpms_ble/blob/master/custom_components/tpms_ble/tpms_parser/parser.py. This is a candidate interpretation, not manufacturer-confirmed Bike 2 firmware compatibility. Pressure reference/compensation is unverified; no altitude or temperature correction is applied. Compare with the manufacturer's app, including different pressures/temperatures, before relying on it. Manufacturer/service identifiers alone do not authenticate a sensor.

BLUETOOTH_SCAN and legacy BLUETOOTH_ADMIN declarations were added. No neverForLocation assertion is made because Android documents that it can filter beacon results. First-run setup and the TPMS screen request scan access as needed. Already-completed permission setup is not reset solely for this addition; the scan screen requests missing access.

Phone home/settings/Advanced buttons now use a shared rounded style, larger touch targets, spacing, raised appearance, ripple feedback and distinct disabled state. The GPS toggle uses a different checked color. The TPMS test uses the same buttons. Android Auto host-rendered buttons are unchanged.

Kotlin source compilation and executable regressions passed, including synthetic manufacturer/service payloads, pressure/temperature conversion, flag masking and truncated/oversized input rejection. No live FOBO packets, phone UI/device scan, Android resource build, signed bundle or head-unit test was verified here. Use code 87 for Studio build and Play internal upload.

## Version 0.88: user's Bike 2 service 0x0126

The supplied scan contained a service-0x0126 packet with six embedded address bytes followed by four data bytes. The user confirmed its candidate 222 kPa (~32.2 PSI) matched the front tire. The diagnostic scan now recognizes that service with exactly ten payload bytes and requires its embedded address to match the advertiser. Pressure uses the final word's low ten bits; temperature is deliberately unavailable for this variant until its encoding is verified. Other existing formats remain experimental. This is one observed pressure point, not full protocol/calibration/reference confirmation.

Build/upload code 88, scan again, then assign the recognized sensor to Front. Rear discovery and readings need a separate packet/comparison. Includes the tighter default recognition rectangles from the supplied screenshot (Reset then Save to apply over existing saved boxes).

Kotlin compilation and regressions passed with the supplied 0x0126 payload fixture, expected 222 kPa pressure, absent temperature, mismatched address and truncated input rejection. No live rescan/device or full Android bundle build was performed here.


## Version 0.89: provisional Bike 2 temperature enabled

At the user's request, service 0x0126 now displays provisional temperature: payload byte 7 low seven bits minus 40 degrees Celsius, converted to Fahrenheit. The supplied packet gives 26°C / 78.8°F. UNVERIFIED labels remain; this supersedes 0.88's unavailable-temperature behavior. Kotlin compilation and packet regression checks passed. No simultaneous manufacturer-app comparison or live device validation was performed.



## Version 0.90: visible TPMS assignment controls

Always-visible Assign front sensor / Assign rear sensor buttons select from up to 100 discovered BLE devices (recognized candidates first) or accept a validated Bluetooth sensor address manually. Unknown devices are labeled format unrecognized; assignment never implies decoder support. Sensor rows are also tappable. Clear assignment is available. Previously saved assignment IDs remain. For the supplied front packet, use 00:15:88:0B:9E:88. Pressure/temperature support for that service requires version 0.89 or newer. Kotlin source compilation passed; selection/assignment UI needs phone testing.



## Version 0.91: sensor readings in assignment choices

Assign front sensor / Assign rear sensor lists show each discovered device's name/address, current assignment, candidate PSI/kPa and temperature, reading age, and unverified/not-live state. Unknown formats show unavailable values. Readings/ages refresh each second while the picker is open. Device order is frozen to prevent assignment moving under a tap; reopen the picker for newly discovered devices. Source compilation passed; actual selection UI still needs phone verification.


## Version 0.92: Front/Rear tire panels

Phone home shows a two-column Front/Rear panel with PSI, °F and last-broadcast age. Android Auto adds a Tire pressure row with both readings; tap it for a tire details screen and parked-only Refresh tires action. More → Tire pressure opens the same screen. Phone Refresh tire readings listens for two minutes while the activity stays visible; leaving stops that refresh. Continuous monitoring follows the GPS tracking foreground service, including rides kept running after a moving disconnect. It stops when the ride service ends, independently of any visible manual refresh.

Assign Front and Rear first under phone Settings → Advanced → Scan TPMS sensors. Existing assignments are reused. Main monitoring only saves packets from assigned sensor addresses and uses supported FOBO decoders. The diagnostic scanner can also populate saved readings before assignment. Most recent readings persist locally across app restarts; no BLE address or TPMS data is sent to a server. A newer packet timestamp supersedes the saved reading; out-of-order/duplicate timestamps are ignored.

Values show age, not an unconditional live indicator. Readings older than two minutes are labeled Stale; future-dated saved times are stale/time unavailable. Unassigned and waiting sensors have explicit placeholders. No pressure alarms, battery display or claimed manufacturer endorsement is added. Bike 2 temperature decoding is still the provisional interpretation the user requested, with matching readings confirmed by the user. Sensor pressure reference/compensation is not independently established.

Shared filtered low-power BLE scanning recognizes manufacturer 0x0127 or service data 0x0126/0x012A. Owner tracking prevents a manual screen's stop/timeout from cancelling the ride scan. Android 12+ Nearby devices/scan access is needed. GPS service declares location and connected-device foreground types, using the latter when BLE access is granted. Android restrictions, Bluetooth off and missing grants can prevent updates; saved readings then age and become stale rather than being presented as current.

The illustrative preview is ../tpms-display-preview.png. Sample rear readings and other ride values are examples, not captured sensor output. Android Auto controls actual panel sizing and scrolling; preview is not a device screenshot.

All main/debug Kotlin sources compiled and executable regressions passed, including supplied FOBO packet and new age/stale rules. Manifest XML parsed. Actual phone layout, Android Auto templates, screen-off/background scan delivery, scan filters, permission behavior and sensor timing require device verification. No complete Gradle/resource build or signed bundle was generated here. Build/upload version code 92.

## Version 0.93: tire warnings and monitoring without GPS

Phone Settings → Tire pressure warnings & monitoring configures independent Front/Rear low thresholds in PSI (0–150; default 0 disables low-pressure alert until you choose a value). No motorcycle pressure recommendation is implied. Zero pressure is always treated as critical. Low means strictly below threshold; zero takes priority. Phone cards get yellow/red borders and LOW/ZERO labels. Auto home has a colored F/R indicator plus text labels; Tire pressure details shows bordered reading panels. Native Auto row backgrounds are controlled by the host.

A newly saved reading with a source packet no older than 30 seconds triggers a per-tire notification on transition into low or zero state. Repeated packets in the same state do not repeatedly alert. Normal readings clear notifications; reassignment resets alert state. Zero alert is handled at packet receipt, not delayed to the 30-second check. Cached/stale values can retain the warning color with age shown but do not themselves generate new notifications. Monitoring and a received sensor broadcast are required; loss of signal is never interpreted as zero.

Choose zero-pressure warning sound opens the app's Zero tire pressure warning notification-channel settings, where the phone's available sound options can be configured. Initial channel uses default alarm URI, high notification importance and vibration. Android notification permission, channel settings, volume, routing and Do Not Disturb govern audible delivery; the app does not override them. Notification reports the sensor value, not independently verified tire failure.

Monitor tires without GPS starts a separate connected-device foreground service, with ongoing Tire monitoring active notification and Stop monitoring action. Assigned sensors and Nearby devices access are required. It shares the filtered low-power BLE listener with GPS/manual owners and checks the scan every 30 seconds. Sensors broadcast on their own schedule: this does not send a GATT poll or guarantee a new reading every 30 seconds. Handler timing can be deferred by Android power restrictions. Received packets are processed immediately. The option restarts monitoring when the phone app is opened; no reboot receiver/guaranteed killed-app restart was added. GPS starts/stops independently. Turning this option off stops only its scan ownership.

Kotlin compilation and domain checks passed, including low/normal/zero priority and disabled-low cases. Full resource build, phone/head UI, notification sound, Bluetooth foreground service, long-running battery use and actual zero-pressure alert behavior require hardware testing. Build/upload code 93.

## Version 0.94: repeating zero-pressure alerts

A zero-pressure notification repeats every 30 seconds while the assigned sensor's latest stored reading remains zero and no older than two minutes. The initial alert is still triggered by a new fresh packet. Newly received nonzero readings immediately cancel pending repeats; reassignment cancels all pending zero timers. A stale/missing/future-dated reading stops repeats. Low alerts remain state-change-only. Each sensor has one pending repeat timer, and repeat notifications explicitly permit re-alerting with a new notification timestamp. Android scheduling, notification permission/channel settings, sound cooldown, volume and Do Not Disturb still govern timing/audibility. No exact wakeup or sound delivery guarantee is claimed.

Kotlin compilation and executable checks passed, including fresh zero, stale boundary, nonzero, missing and future-dated repeat eligibility. Actual repeating notification/audio and foreground/background device behavior need testing. Build/upload version code 94.

## Version 0.95: Bluetooth head-unit service inspection

Phone Settings → Advanced → Inspect head-unit Bluetooth services opens a dedicated foreground report screen. Pair/connect the bike, then tap Inspect services. It matches paired devices using the configured head-unit Bluetooth name; multiple matches offer selection. Reports name/address, bond state, transport/class, initial cached UUIDs, headset/hands-free and A2DP connection states, SDP request acceptance, callback UUIDs or 25-second timeout. Known UUIDs get readable labels; unknown/custom IDs remain explicitly unknown and do not imply telemetry.

Copy report copies the selected device's report locally; the last report is retained in private app preferences. Addresses are included in the report for device identification. No reports are automatically uploaded. The tool requests BLUETOOTH_CONNECT where needed; it does not perform general device scanning, pairing, RFCOMM/GATT connection, characteristic reads or writes to discovered application services. Bluetooth SDP service discovery itself is requested. Bluetooth broadcasts can originate outside the system UID, so its lifecycle-scoped receiver is exported, filters ACTION_UUID and only accepts the selected address during a pending request. Late profile callbacks are discarded and profile proxies always close. Leaving stops observing and marks pending inspection interrupted.

Android can return cached UUIDs even after fetchUuidsWithSdp, so no fresh/complete inventory is guaranteed. This is a Bluetooth service/SDP check, not a BLE GATT characteristic inventory. Reference: https://developer.android.com/reference/android/bluetooth/BluetoothDevice.

Kotlin source compilation, existing domain regressions and service-label checks passed; manifest XML parsed. Actual Honda discovery/profile callbacks and phone layout remain unverified. No full signed bundle produced here. Build/upload code 95, inspect on the bike, then share Copy report output.

## Version 0.96: copyable OCR crop coordinates

Settings → Advanced → OCR crop coordinates shows saved/default rectangles and a Copy report button. The photo recognition-area editor also includes Show / copy box coordinates, reporting current edited rectangles even before saving. Reports contain app version, origin/convention, normalized left/top/right/bottom and width/height, percentages, and pixel x/y/width/height when image dimensions are available. Pixel bounds match OCR clamping/rounding with exclusive right/bottom edges. Dimensions refer to the upright decoded OCR bitmap, not the original JPEG or screen. Advanced uses the last decoded photo dimensions and labels them accordingly; current editor reports current image dimensions. No photo or report is uploaded. Paste the copied report into chat to reproduce the exact defaults.

Kotlin source compilation passed; on-device dialog layout/copy flow not tested here. Build/upload code 96.


## Version 0.97: Auto Pane row crash fix

Removed the tire-pressure dashboard row click handler: PaneTemplate does not permit row click listeners. Tire details remain accessible from the tools menu → Tire pressure. Installed-library regression reproduces the exact reported exception with a clickable row and passes with the listener removed. Earlier action constraints regressions still pass. Kotlin compilation/domain checks passed. Also includes the exact supplied default OCR rectangles and recent phone disclaimer/fill-up-odometer display removals. No actual host or full signed-bundle build verified here.


## Version 0.98: optional main map dashboard

Choose Phone Settings → Appearance → Head-unit dashboard → Map, or the head-unit tools menu → Dashboard. Gauges remains the default. Map mode uses Android Auto's host-rendered PlaceListMapTemplate, with browsable fuel/session and tire summaries opening real detail screens. A play/pause icon controls GPS using the same permission and parked-start rules as the gauge screen. Find gas remains available. The nearest saved station is a real map POI with a straight-line DistanceSpan; opening it shows station choices and navigation. This mode does not trigger additional network searches. Saved station results retain freshness and OpenStreetMap attribution.

Current app GPS anchors the map when fresh and permitted; Android Auto controls its own location dot, camera, map provider, layout and refresh behavior. No default or stale app location is presented as current position. Updates are requested at most every 15 seconds to reduce interruptions; direct actions refresh immediately. This is an overview, not embedded Google Maps navigation, turn guidance, a custom split-screen layout, or proof of motorcycle GPS origin. No new paid map API/key or app tile renderer is added. A map-construction exception is saved and falls back to gauges for that screen instance. Map host availability and any Play review requirements still need testing.

Kotlin compilation and domain checks passed. Installed-library row-list and map action-strip constraint checks passed. Full map construction uses Android text classes and requires an Android runtime; no head-unit rendering or signed AAB build verified here. Build/upload version code 98 (0.98), select Map, and verify stationary/moving location updates, GPS controls, tire freshness, station navigation, and return to Gauges on the Honda/DHU.

## Version 0.99: dashboard mode shortcut

Both main head-unit dashboards have a mode-switch icon directly beside Find gas. Tap the map icon on Gauges to open Map; tap the view icon on Map to return to Gauges. Selection is saved and switches immediately. The Gauges settings/tools entry is now a Settings button beside the GPS control, keeping the header within its two-action/one-text-title limit. Map uses four allowed header actions. Opening Gauges details from Map and switching to Map returns to the root dashboard rather than stacking dashboards.

Kotlin source compilation/domain checks and installed-library constraints for both header strips and Pane GPS/Settings actions passed. No signed bundle or on-device rendering verified. Build/upload version code 99 (0.99).

## Version 0.100: Find gas starts searching; labeled Map view

Main dashboard Find gas buttons now open the station list and request one search on first start. An existing search owned by another active phone/car controller is observed instead of duplicated, and its saved results are loaded on completion. The existing service cooldown is retained: when necessary the list displays its countdown and automatically starts the requested search once ready. Leaving the screen cancels its queued request; returning from the map/list or navigation does not repeatedly initiate searches. Opening a saved POI remains a browse action, without initiating a new search.

On the Gauges dashboard, Map view is now a text button beside GPS. Find gas retains its top-strip label and the settings wrench returns to that strip. This satisfies the Pane action limits and one-custom-title limit of its header strip. Map's return-to-Gauges icon remains available. Version code 100, version name 0.100. Kotlin source compilation and existing domain regressions passed; runtime lifecycle/network and head-unit layout still require device verification. No signed bundle produced.

## Version 0.101: station search resilience

Retries now cover socket timeouts, connection failures/resets, busy HTTP 502/503/504, and rate limiting HTTP 429, with five retries maximum. Backoff is 5/10/20/30/45 seconds, respecting a longer Retry-After and a minimum 30 seconds for HTTP 429. DNS, TLS, denied/invalid queries and cancellation are not blindly retried. Response timeout is 45 seconds rather than 25 to accommodate public-server queuing plus query execution; connect timeout is 15 seconds. Server execution timeout remains 20 seconds.

Empty results expand through 5, 10 and 25 km instead of jumping directly from 5 to 25. Optional nearby convenience-store name enrichment is omitted on retries and larger searches; real fuel POIs and their own name/brand/operator tags remain. Saved results remain visible on failures. Advanced → Station search report now retains six recent request attempts with timing, status, failure and query shape; coordinates and response payloads remain excluded. Failed attempts previously overwrote each other, making retry diagnosis difficult.

Kotlin source compilation and domain checks passed, including new transient/nontransient failure and backoff/Retry-After tests. No live public-service load test, signed bundle build or Honda runtime test performed. These changes address observed code gaps; the user's exact failure cause is not confirmed without their report. The public community service can still be busy/unavailable. Build/upload code 101 (0.101).

Public service behavior reference: https://dev.overpass-api.de/overpass-doc/en/preface/commons.html

## Version 0.102: all saved stations on main map

Removed MapDashboard's minByOrNull nearest-only selection. It now supplies all saved search results as station rows and Place markers, sorted by current position when available. The search currently returns up to three stations; fewer may exist in the searched area. Dedicated search list/map already included every returned station; their titles now include the actual result count to distinguish limited results from display behavior. Android Auto controls visible rows and map framing, so off-screen POI visibility remains host-dependent.

Kotlin source compilation and existing domain regressions passed (work/check-102.log). No head-unit rendering or signed bundle build verified. Build/upload code 102 (0.102).

## Version 0.103: expand until three stations

Fixed the search stopping after a single result: expand from 5 to 10 to 25 km whenever fewer than three mapped stations are found. Stop at three or the 25 km maximum. Intermediate results are displayed/saved while expanding so a wider-request failure does not discard useful stations. Status describes expansion progress and explicitly reports fewer-than-three final results within the maximum area. Existing retries and global search guard remain.

Kotlin source compilation/domain checks passed (work/check-103.log). Added policy coverage for zero/one/two results at each radius, stopping at three/cap, and geographic fixtures producing one/two/three stations across the three radii. Real service data and head-unit rendering not tested; no signed bundle produced. Build/upload code 103 (0.103).

## Version 0.104: compact stations and live map anchor

Both map layouts now place all station rows first, using one-line name/distance rows with numbered 1/2/3 markers. Ride summary rows and search status/attribution follow the stations instead of pushing them below the initial viewport. Search list retains its richer direction/details rows. Current-location display remains enabled with permission; search map now anchors only a fresh app position, matching the main map, rather than anchoring an old fallback reference. Old/reference search distances remain labeled in status.

Important API limit: PlaceListMapTemplate host renders markers as their rows become visible and frames the anchor plus visible markers. There is no app-controlled fit-bounds/zoom method in this template. Compact station-first rows improve the chance of all three markers sharing the initial viewport, but cannot guarantee that every host keeps three POIs and its car dot visible simultaneously. A guaranteed camera/marker layout needs an app-rendered map, a separate larger change. Reference: https://developer.android.com/reference/androidx/car/app/model/PlaceListMapTemplate.Builder#setAnchor(androidx.car.app.model.Place)

Kotlin compilation/domain checks passed. Runtime map framing and marker labels require Honda/DHU testing; no signed bundle generated. Build/upload code 104 (0.104).

## Version 0.105: ride map shows ride information

Removed all saved station rows/markers from MapDashboard. Main Map view now shows only fuel/range/current session and tire/GPS information alongside the host map and current-location display. Find gas still opens a separate station search with compact numbered station rows and markers; its data, search retries and radius expansion are unchanged. Removed station attribution from ride status because it no longer displays station data. This supersedes 0.102/0.104 station additions to the main dashboard.

Kotlin source compilation/domain checks passed (work/check-105.log). No signed bundle or head-unit rendering tested. Build/upload code 105 (0.105).

## Version 0.106: miles-based station search

Search begins at 15 miles, then expands to 30 and 60 miles if fewer than three stations are found. Stops at three or the 60-mile cap. Search progress, empty/limited-result messages and diagnostic radius use mi, with exact conversion to meters internally for Overpass. Optional convenience-store enrichment stays bounded to the original local area on the initial attempt to avoid large shop scans. Station distances already use miles.

Kotlin compilation/domain checks passed (work/check-106.log); updated radius-policy and geographic fixture tests verify 15/30/60-mile progression, one/two/three results, stopping conditions and displayed mi labels. No live server/head-unit test or signed bundle build. Build/upload code 106 (0.106).

## Version 0.107: configurable units

Phone Settings → Units provides independent selections for distance (mi/km), speed (mph/km/h), fuel volume (USG/L/Imp gal), economy (US MPG/Imp MPG/L/100 km/km/L), tire pressure (PSI/kPa/bar), and temperature (°F/°C). Defaults preserve the existing US setup. GPS accuracy/separation uses feet with miles, meters with kilometers. Heading remains degrees and session duration HH:MM:SS. Selection persists; phone summary refreshes, Settings reopens with converted controls, and visible Auto screens refresh through their existing update mechanisms.

Applied to fuel/range/scale/details, live and last sessions, compass-panel speed, tires and assignment list, station directions/DistanceSpan labels/search radius messages, tank entry, dashboard-reading entry/preview/confirmation, tire thresholds, range-alert labels and disconnect-speed entry/notifications. Existing range threshold choices still represent the same physical levels (20/40/60/80 miles), shown in selected distance units; disconnect threshold retains existing whole-mph storage, converting the selected entry back and rounding to the nearest stored mph. Station search physically remains 15→30→60 miles, displayed in selected distance units.

Persisted measurements, estimation math, BLE kPa/Celsius and existing warning thresholds retain their canonical representations. Changes occur at display/input boundaries; changing preference does not rescale stored data. Unedited pressure thresholds retain their exact old value even when the entry's displayed conversion is rounded. New completed sessions save raw distance/fuel/time separately so later unit changes reformat their totals. Older US-unit summary strings are converted on display without modifying their saved originals.

Photo parsing normalizes explicit numeric km, L/100 km and km/L values before the existing label/ambiguity checks. A separate Photo MPG convention selection distinguishes unqualified US/Imperial MPG independent of chosen display economy. Suggestions are converted to selected entry units and converted back once on Save. Missing/ambiguous source units still require manual confirmation; unsupported crop layouts and unreadable digits are not guessed. The TOTAL-mile second-pass digit reader stays restricted to its labeled mile source. Recognition-area labels now say Economy / Trip. Raw copied sensor samples and historical reports preserve their recorded source fields (e.g. accuracyMeters, ms/nanosecond timestamps); interpreted GPS summary uses selected units.

Kotlin compilation plus existing and new executable checks passed (work/check-107.log). New checks cover each conversion and inverse, known physical equivalents, metric fuel-estimation round trips, temperature/null values, session/legacy formatting, GPS short distance, explicit metric OCR, Imperial MPG and thousands-separated metric odometers. No full signed bundle, on-device settings/keyboard layouts or head-unit rendering tested. Build/upload code 107 (0.107). On device, switch units while tracking; verify labels, station markers, saved sessions, photo confirmation, tank capacity and warnings retain the expected physical values, then switch back.

## Version 0.108: bounded idle TPMS checks

With GPS stopped, automatic tire monitoring starts a listening window every 30 seconds. Each window waits for fresh decoded packets from all assigned front/rear sensor IDs, ending as soon as both arrive or after 15 seconds. A cached measurement timestamp before the window, unrelated device, duplicate front packet or packet delivered after the deadline cannot satisfy the rear reading. Unassigned tires are excluded. Manual phone/car tire refreshes while GPS is stopped use the same bounded window; continuous ride monitoring remains unchanged. A shared BLE scan stops only when no other owner still needs it.

At timeout, missing sensor IDs get a persisted stale cutoff. Old pressure/temperature values remain visible with STALE in the reading's first line; sensors without a saved value show Stale / no reading received. Only a measurement newer than the timeout clears the stale status, including measurements received by another scan owner. Tires that responded remain fresh. Repeated zero warnings do not run from a timeout-stale reading. Later monitoring windows continue normally, and fresh zero packets still use existing warning logic.

A scan failure or unavailable Bluetooth still leads to a timeout stale mark for missing sensors. Stopping/cancelling a window intentionally cancels its timeout without marking readings stale. Android scheduling and BLE sensor advertising determine actual timing; this is passive listening, not a GATT command that forces a new sensor measurement.

Kotlin compilation/domain checks passed (work/check-108.log), including both-sensor completion, duplicate/unrelated/pre-window/late packets, single assigned sensor, no-assignment behavior and stale cutoff recovery. No real BLE battery/timing/endurance or Android lifecycle/host rendering verified. Build/upload code 108 (0.108); no signed bundle generated.

## Version 0.109: continuous background tire listening

The existing background tire-monitoring toggle now reads Keep listening for tires without GPS. When enabled, its connected-device foreground service owns one continuous low-power filtered BLE scan, whether GPS is tracking or stopped. It no longer starts/stops the scan every 30 seconds. A five-second health/freshness watchdog reuses the existing scan and attempts recovery after a scan failure; it is not a BLE polling interval. The persistent notification describes continuous listening and retains Stop monitoring. Existing enabled/disabled preference is preserved.

While continuous background monitoring is active, readings with no new packet for 15 seconds are stale; an assigned sensor with no reading gets the same 15-second initial grace period. The watchdog persists stale cutoffs so stale data does not become fresh merely because monitoring stops. Fresh newer packets recover readings. The prior manual refresh wait-for-both/15-second timeout remains; ending a manual refresh does not stop the background or ride scan owner. Existing two-minute age labels still apply outside continuous background monitoring. Low/zero warning thresholds and 30-second fresh-zero warning repetitions are unchanged; stale zeros do not repeat.

Kotlin compilation/domain checks passed (work/check-109.log), including continuous freshness at 14,999/15,000 ms, no initial packet, invalid age, and recovery. No physical BLE battery/endurance or Android service restart testing performed. Android Bluetooth/permission/background scheduling can interrupt listening; the foreground notification exposes active monitoring. Build/upload code 109 (0.109); no signed bundle generated.

## Version 0.110: tire listening enabled by default

Keep listening for tires without GPS now defaults to enabled when no background preference has been saved. The Settings toggle, phone-resume startup and service enable check use one shared preference accessor. Explicit Off/Stop monitoring choices remain saved and respected. Bluetooth/Nearby devices permission is still required; existing continuous low-power scanning and stale behavior are unchanged.

Kotlin source compilation passed (work/check-110.log). No device battery/endurance test or signed build produced. Build/upload code 110 (0.110).

## Version 0.111: monitoring health, recovery, data tools and confidence

Phone main screen now shows tire-monitoring health (permission missing, Bluetooth off, sensors unassigned, live listener, system background detection requested or stopped with last error). Tire detail screen on Auto also shows health. Phone Settings → History, backup & monitoring opens the new dark-mode-aware, inset-safe data tools page.

Recovery: a narrowly address-filtered system PendingIntent BLE scan receives and decodes assigned sensor packets if the app process is reclaimed. It is re-armed on boot, package replacement, Bluetooth becoming available, assignment changes, and normal app/service startup. Explicit stop disables/disarms background detection. Continuous foreground scanning remains low power; failures back off 30 seconds, foreground service uses START_STICKY with disabled/missing-permission/no-sensor checks, and a user Restart tire monitoring control is available. No background activity launch or attempt to bypass Android force-stop. Permissions, system delivery, Bluetooth and vendor power policy still apply; an accepted scan request is not proof Android is delivering packets. Boot/Bluetooth receiver is exported for system/privileged Bluetooth broadcasts and only handles its listed system actions. Scan-result PendingIntent explicitly targets a non-exported receiver and is mutable only to accept system result extras.

History: saves up to 1,000 NEW completed tracking sessions, fill-ups and confirmed dashboard updates in canonical units. View formats measurements using selected units; CSV has explicit canonical unit/epoch-time headers with quoting/formula protection. Fill-up history records the full-tank baseline, not actual gallons dispensed. Only confirmed odometer values are recorded as odometer events. Undo marks the corresponding update undone. Old sessions that were never retained cannot be reconstructed; the existing last-session summary remains available.

Backup/restore: user-selected JSON files include an allowlisted set of preferences (tank/baseline/calibration, accumulated distance, last session, units, appearance, OCR boxes, sensor assignments, warnings, connection and station-search settings) and history. Excludes photos, GPS location caches, live sensor readings, runtime permissions, active tracking flags, credentials and diagnostic samples. Parse/schema/type/range/size/crop/unit/ID/history validation occurs before writes. Restore previews replacement, requires stopped tracking/no active reading edit, attempts rollback on write failure, clears old live sensor readings/undo state, and re-arms monitoring. JSON backup format schema 1; 2 MB limit, 1,000 history entries maximum. This is a manual backup, not automatic cloud sync or a guaranteed multi-file transaction.

Measurement tools: Tire monitoring report shows accepted updates and mean/max observed packet intervals; Reset sensor timing statistics starts a fresh sample. Intervals can include sleep/interference/Android delivery gaps and do not establish the sensor's transmission schedule. Start/Finish phone battery observation records duration, battery endpoints, available charge counter and charging endpoints; whole-phone readings cannot attribute energy to this app. Observations reject a phone reboot. Compare equal-duration monitoring-on/off tests with GPS stopped before claiming battery draw is insignificant.

Fuel confidence is descriptive, never a fabricated accuracy percentage: uncalibrated, calibration age unknown/over 30 days, interrupted GPS, gaps, manual correction or recently confirmed readings without recorded gaps. Main phone and Auto fuel rows show it. Confirmed updates stamp calibration age; Undo restores its prior timestamp. The 30-day label is an app review heuristic, not a measured fuel-error guarantee.

FOBO battery: packet evidence is captured/persisted and included in phone report and Auto tire details. User reference on October 15, 2026: both official FOBO battery icons showed four of four bars. Bike 2 service 0x0126 battery mapping remains UNVERIFIED; app health displays Unknown. Existing Type-E community interpretation can expose plausible experimental voltage only for its manufacturer frame, labeled unverified; it is not applied to the user's 0126 packet and is not converted to percent or healthy/low claims. Earlier supplied 0126 sample would yield an implausible voltage under the other format, so no guessed four-bar decoding or fake percentage is added. Correlate future raw bytes with an actual change in FOBO bars before enabling health claims.

Validation: Kotlin main/debug compilation and executable domain checks passed (work/check-111.log). Added confidence branch, packet timing, CSV formula/quote, backup allowlist/type/range/crop/units, and battery-evidence/variant tests. Existing installed Car App Library validators pass; manifest XML parses. Full backup file-picker/restore lifecycle, actual system background delivery/reboot, energy/endurance and Honda rendering remain unverified; JSON/Android runtime paths cannot be executed against the JVM android.jar stubs. No signed AAB generated. Build/upload code 111 (0.111), then verify backup round-trip while stopped, units/session history, monitoring after Bluetooth restart/process recreation, and real packet timing/battery observations.

References:
- Android background BLE: https://developer.android.com/develop/connectivity/bluetooth/ble/background
- PendingIntent scan API: https://developer.android.com/reference/android/bluetooth/le/BluetoothLeScanner
- Community Type-E interpretation (not confirmed Bike 2 0126): https://github.com/bkbilly/tpms_ble/blob/master/custom_components/tpms_ble/tpms_parser/parser.py

## Version 0.112: five-minute tire freshness and clearer GPS display

Tire readings now remain current until five minutes after the last packet, across continuous detection, normal age labels and zero-warning freshness. A short manual refresh may still finish after 15 seconds, but its timeout cannot mark a recently received tire reading stale. Initial missing-sensor grace for continuous listening is also five minutes. Existing forced stale cutoffs no longer override a younger reading; new packets recover normally.

Separated map GPS status from fuel-estimate confidence. The ride map displays reported location accuracy and fix age, with a clear approximate label when accuracy is worse than 25 m, instead of fuel-confidence text where GPS status belonged. Phone tracking status also includes this explicit GPS line. Android Auto host callbacks now update the live display position even if the GPS ride service is stopped. Display accepts valid coordinates with reported accuracy up to 100 m and fix age up to 30 seconds; invalid/older fixes are not presented as current. Distance accumulation, host preference for ride tracking, accepted-speed/heading rules and their stricter quality filters are unchanged. This does not identify the physical GPS hardware or improve a poor radio signal; source-origin verification remains in Advanced reports.

Kotlin source compilation/domain checks passed (work/check-112.log). Added five-minute stale/initial-grace/zero-repeat boundaries and GPS display age/accuracy boundaries; existing distance/fuel regressions pass. Exact user GPS wording/location is not yet confirmed; this addresses two identified display issues, not a verified hardware GPS fault. No signed bundle or actual host render tested. Build/upload code 112 (0.112).

## Version 0.113: bounded distance across GPS fix outages

While one tracking session remains active and GPS stays enabled, a gap longer than 30 seconds between accepted fixes is now bridged using the straight-line separation of the last accepted anchor and the recovered fix. The implied average speed must be at most 100 mph (160.9344 km/h). The same ceiling now rejects implausible ordinary jumps. Existing coordinate, 25 m accuracy, monotonic timestamp/freshness and stationary drift checks remain. A rejected spike clears the anchor so it cannot seed a later phantom estimate. GPS provider disable/enable, new session/service startup and explicit resets discard continuity; no estimate is made across them. Short source changes reset to avoid offset mileage; a valid long-gap recovery can use a different source under the same speed/accuracy gate.

This is endpoint interpolation, not reconstruction of a road route, prediction from prior speed or synthetic GPS locations. Curves, detours and loops can make real distance longer; returning to the same place cannot reveal miles travelled during the outage. The app does not add miles continuously while no fix is available. A small endpoint difference below drift threshold adds zero, even across a long gap. There is no arbitrary gap-duration cap within one uninterrupted active session; the elapsed time and speed ceiling bound distance, and the result stays marked estimated.

Gap-added meters count once toward ride/fuel distance, with separate since-fill and session estimated-meter counters. Current/last session, history/CSV and confidence/quality wording identify the estimated portion. Full fill-up and confirmed total-distance correction reset since-fill estimates; additive missed distance retains existing estimates. Undo captures/restores estimate counters while retaining estimates added after the edit. Backup allowlists and history validation/export include estimated portions and reject estimated totals above overall distance. Existing history without these optional fields remains supported.

Kotlin source compilation and executable regressions passed (work/check-113.log). Tests cover plausible recovered distance, exactly-once accounting after recovery, stationary drift, 99/101 mph bracket, bad-spike anchor rejection, explicit reset, source changes, invalid/duplicate/stale fixes and confidence labeling. Actual tunnel/outage driving and Android persistence/undo UI need device testing. No signed bundle generated. Build/upload code 113 (0.113).
