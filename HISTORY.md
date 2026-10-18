# Historical development notes

These notes describe earlier versions and can contain obsolete instructions. Use README.md for the current app.

# AfricaTwinTelemetry — fuel & range estimate

Historical snapshot: version **0.68 (code 68)**. Android Studio Kotlin project for a personal Africa Twin fuel estimate and nearby-station prototype.

The Auto home list starts with fuel, range, and session distance/time. Counters refresh every second while the host reports the first row visible; refreshes pause when scrolled down to avoid resetting the list position. Tap the session row for a dedicated live counter. Hosts that do not report visibility still support manual Refresh and the dedicated Session screen. GPS testing is available inside Live compass. Phone session totals appear as a prominent separate display below the tracking toggle. Session distance and time reset on tracking Start and freeze on Stop.

The phone home screen shows estimated gallons and range, with **Start / Stop GPS tracking**, current tracking status, **Fill up** and **Update dashboard reading**. Tank capacity defaults to the user's stated **5 US gallons**, and is adjustable. Nearby gas-station search remains optional under **Tank size & options**.

## Dashboard photos

Both actions let you take a full-resolution photo, choose an existing image, or enter readings manually. Bundled Google ML Kit Latin text recognition runs on-device. Review the image and edit odometer miles and **US average MPG** before confirming; OCR never writes a fuel baseline automatically. Unrecognized or ambiguous fields stay blank.

**Fill up:** confirms a full tank at configured capacity and stores the current odometer as the baseline. **Update dashboard reading:** retains that baseline, corrects total distance from the odometer difference, and uses the confirmed updated MPG for the entire fill-up interval. Changing tank capacity affects the next fill-up only.

Estimated fuel = max(0, full-tank gallons − miles since fill-up / average MPG). Estimated range to empty = estimated gallons × MPG; no reserve is deducted. Estimates are not live sensor readings and cannot account precisely for idling or variable consumption. A partial fill must not be recorded as a full fill-up.

## Android Auto

The car screen puts **Start / Stop GPS tracking** first, followed by estimated fuel, estimated range and the nearest station. Start is parked-only and opens the phone activity, which starts GPS when visible or requests precise location. If Android blocks opening the phone, start manually from the phone home screen. Stop works directly from either display. The status refreshes on the phone every two seconds and on Auto every five seconds. Fuel/speed/odometer telemetry queries and CAR_FUEL/CAR_SPEED/CAR_MILEAGE permissions remain removed. Version 0.13 adds only a startup vehicle identity query, stored under phone diagnostics. No Honda telemetry availability is claimed.

Phone updates and capture are intended for use while parked. Android Auto remains a templated POI prototype because of its nearest-station functionality; no public publishing eligibility for a standalone fuel dashboard is claimed. Local DHU testing is supported. Real-bike testing requires installation through Play and Android Auto form-factor opt-in; do not use Studio Run to overwrite a Play-installed copy.

## Build and install

Open this project folder in Studio and synchronize Gradle. Build > Generate Signed Bundle/APK > Android App Bundle, use the existing upload key and select **release** (bikeTest also works). Upload code 35 to Internal testing, roll out privately and update through Play. No signed version-35 bundle has been generated here.

Uses installed Android 37.0 SDK / Build Tools 36.0.0, AGP 9.4.0, Gradle 9.6.0, built-in Kotlin, Car App Library 1.8.0-rc01, and bundled ML Kit text-recognition 16.0.1. `local.properties` and signing keys are ignored by Git. Non-debuggable host validation includes the Android Auto package/certificate independently matched to the official library's sample allowlist.

See [photo workflow and validation](PHOTO_FUEL_SETUP.md), [GPS tracking notes](GPS_RIDE_SETUP.md) and [station search](FUEL_SEARCH_SETUP.md). Older version-specific guides and validation entries are historical.

## Verification

UI-cleanup debug/release source compilation passed before the OCR dependency was added. Executable parsing tests cover labeled/multiline readings, trip/instantaneous values, ambiguous readings and unsupported units. GPS/distance/fuel-math tests pass. No telemetry references or car-data permissions remain in main sources/manifest.

Final Android build/lint, camera/picker/FileProvider behavior, ML Kit linkage, image accuracy, phone rendering and Play/real-host behavior require Studio sync and device testing. The OCR dependency is not in this session's dependency cache, so a successful full version-8 build is not claimed.

## Privacy

Dashboard recognition is on-device; this app does not upload dashboard images. Camera captures are temporary cache files, deleted on confirmation/cancel in normal operation; interrupted capture may leave cache files for Android to reclaim. Selected gallery originals are not modified. No full-resolution route history is stored by GPS tracking. Separately enabled station lookup sends coordinates to public Overpass. Follow ML Kit and OpenStreetMap provider policies before wider distribution.

References: [ML Kit text recognition](https://developers.google.com/ml-kit/vision/text-recognition/v2/android), [Android Auto distribution](https://developer.android.com/training/cars/distribute), [vehicle testing](https://developer.android.com/training/cars/testing).

## Missed GPS miles

The phone home screen has **Add missed miles**. Stop tracking, enter only the distance you rode without recording it, review the estimated gallons consumed and resulting fuel/range, then confirm. The miles are added to the total since fill-up; each missed stretch should be added once. The adjustment uses the saved average MPG and is an estimate, not measured fuel consumption. Existing gap warnings remain because a manual adjustment does not prove all missing distance is accounted for.

Restart GPS afterward. Both displays use the adjusted total. A full fill-up clears the adjustment; a confirmed dashboard odometer reading replaces total miles since fill-up and clears the manual-added note, avoiding double counting.

## Fuel and range bars — version 0.11

Phone home has two horizontal bars with numeric values and labeled scales. Fuel uses the full-tank capacity recorded at the last fill-up. Range uses that capacity multiplied by the saved current average MPG. Both bars therefore show the same remaining fraction; they display two units of the same estimate. Fuel becomes amber at 25% and red at 10%; these are visual thresholds, not a measured reserve. No reserve is deducted from the estimated range.

Android Auto home and details include compact bar images beside fuel and range values, using existing Row image support on API 8. Native CarProgressBar requires API 9 and is not used. Unknown estimates have no filled bars. GPS distance, manual missed miles, and confirmed readings all refresh the bars.

## Dashboard save feedback — version 0.12

The review screen labels both editable fields and previews the distance, fuel and range that will result from saving. Tap **Save readings** after checking the recognized values; recognition alone never changes saved data. A successful save shows the odometer/MPG confirmation and the home screen keeps the latest saved odometer, average MPG and fill-up odometer visible across restarts. Invalid or incomplete readings leave the dialog open with an error. A tracking-start race is also reported rather than attempting to save during tracking.

Use **Update dashboard reading** to correct fuel/range after riding. **Fill up** deliberately records a full tank with zero miles since the new baseline, so its fuel amount remains the full capacity regardless of the absolute odometer value. No reproducible OCR/save failure has been established from the report alone; the extra feedback makes recognition, confirmation and persistence distinguishable.

## Saved head-unit information — version 0.13 / code 13

Each Android Auto app session start (including returning to a started session after it was stopped) captures a local diagnostic record with time, app version, host package, host software version, negotiated Car App API and the phone OS version. It also queries CarInfo.fetchModel for manufacturer, model and year, recording each value's status and timestamp. Missing/unsupported responses, exceptions and a 15-second timeout are recorded explicitly; a late response updates the corresponding entry. Previous records remain separate, with the last 20 starts retained in private app preferences. There are no automatic network uploads.

On the phone, open **Tank size & options → Saved head-unit info**. **Refresh** reloads the saved history; **Copy report** copies it to the clipboard for pasting into this chat. No connection or debugging cable is needed to read already saved records. Uninstalling or clearing app storage removes the history.

“Startup” means opening this app on Android Auto, not booting the phone or the motorcycle alone. Vehicle queries need an active Android Auto session. A phone boot receiver cannot identify an unattached head unit and is not added. These queries do not start GPS tracking.

Host software version is the Android Auto app version on the phone, not a Honda firmware version. The public Model API supplies manufacturer/model/year, not a unique serial number or head-unit firmware version. Success in DHU can be simulated and does not prove Honda support. Records must be compared on the bike and another head unit before making automatic recognition decisions; no recognition rule has been added.

Projected Android Auto fetchModel requires no additional vehicle permission according to the current Android documentation. Manifest package visibility is added only for com.google.android.projection.gearhead to allow reading its installed software version. Other hosts may report an inaccessible version. References: https://developer.android.com/training/cars/apps/library/car-hardware-api and https://developer.android.com/reference/androidx/car/app/HostInfo

## Honda automatic GPS startup — version 0.14 / code 14

In phone **Tank size & options**, enable **Automatically track Honda_Wing rides**, then use **Set up automatic tracking location**. Grant precise location first; on Android 11+ the setup explains and opens app settings to choose Location → Allow all the time. Android 10 requests background location separately. Declining retains manual tracking. This version adds ACCESS_BACKGROUND_LOCATION for this optional automatic-start feature; Play may require additional location declarations/review. It does not upload ride coordinates or save route history.

When an active Android Auto session reports manufacturer exactly Honda_Wing with SUCCESS status, automatic start is attempted once per Session instance. It requires a full-fill baseline, enabled preference and appropriate location permission; existing distance is preserved. Unknown, unavailable, similar names and stale callbacks after session stop do not trigger tracking. Already-running tracking is left running. Manual Stop is respected within that session; returning to the app does not repeatedly restart it. A new Session can attempt again.

Background location does not exempt the app from every foreground-service start restriction. If Android blocks service startup, the saved head-unit report and live tracking status expose the outcome; use manual Start while parked. A requested start is not proof of reliable GPS acquisition. Confirm GPS tracking active and increasing distance on the bike.

The trigger is this app's Android Auto session starting, not motorcycle ignition by itself; if Android Auto never starts/binds this app, no manufacturer callback is available. Honda_Wing identifies a reported manufacturer group, not your unique bike. No Bluetooth ignition detector or automatic disconnect stop was added; stop tracking after the ride using the phone, Auto or notification.

## Tracking toggle, disconnect and notices — version 0.15 / code 15

Phone main control now displays GPS tracking ON/OFF and toggles the shared actual service state. Auto's existing Start/Stop row uses the same state. With automatic Honda tracking enabled and location setup complete, a successful Honda_Wing match requests startup; no baseline reset occurs. The last recognized Honda session being destroyed/unbound stops the shared tracker (including tracking started manually during that session). Session onStop is not used, so changing screens or backgrounding does not stop tracking. Other simultaneous recognized sessions prevent an early stop.

The current cached Car App 1.8.0-rc01 implementation was inspected: onUnbind invokes CarAppBinder.onDestroyLifecycle. This is a host-session end proxy, not a public motorcycle-ignition event. If the host unbinds for another reason, tracking also stops; if it never starts this app, there is no startup trigger. Verify reconnect and screen changes on the real Honda.

An informational notification is scheduled eight seconds after service startup or stopping. GPS acquisition may still be pending when startup is announced; the message includes current status. The required ongoing foreground-service notification remains immediate. Enable notification permission through Options; the new Tracking started and stopped channel must also be allowed. Rapid opposite transitions cancel the older delayed message. Eight seconds is best-effort: OS scheduling/process termination and notification settings can delay or suppress delivery. No exact-alarm permission or background polling is added.

## Phone screen safe area — version 0.16 / code 16

Main scroll viewport now applies system-bar, display-cutout and keyboard insets, keeping text and buttons clear of the clock/status icons and bottom navigation controls. Padding is recomputed from the current insets on every change, without accumulating it. The existing content spacing remains inside the safe viewport. Uses Android platform insets with API-28/29 fallback and API-30+ typed insets; no new dependencies. System icons are configured dark over the light phone UI.


## Bluetooth + Android Auto startup and appearance — version 0.18 / code 18

Phone Options now provides **Automatically track Honda rides**, **Enable Bluetooth connection monitoring**, location setup and notification permission. On Android 12+, grant Nearby devices (BLUETOOTH_CONNECT). No scanning, pairing or Bluetooth address history is requested. Name matching is case-insensitive and accepts a connected name containing Honda Motorcycle with a trailing suffix; it is not a cryptographic or unique bike identity.

A system Bluetooth ACL-connected broadcast for a matching device attempts to start a connectedDevice foreground monitor. The monitor observes CarConnection during the bike's boot delay and requests GPS only when bike Bluetooth and projected Android Auto are both present. If enabled while already connected from the phone, live HEADSET/A2DP profile queries seed the Bluetooth state. No saved manufacturer value is reused as proof of a current connection. Existing Honda_Wing startup detection remains a fallback when the Auto app opens.

The monitor has its own low-importance standby notification to stay alive between Bluetooth connection and Android Auto startup. Android can reject background service startup; this is recorded in tracking status and manual Start remains usable. Force-stopping the app prevents broadcasts until it is opened again. A signed Play update and device testing are required; Bluetooth ACL delivery and profile visibility depend on the phone/connection. Play may ask for connectedDevice foreground-service declarations as well as background location review.

When projection disconnects, GPS stops even if our Auto screen was never opened. Existing recognized Honda session destruction and manual Stop still work. Manual Stop suppresses automatic restarts until projection reconnects. Bluetooth loss alone does not stop an already-active projection, allowing wireless projection to outlive a Bluetooth transport change. Disable the automatic option to stop the connection monitor; stop GPS separately if desired.

Appearance defaults to the phone's light/dark setting. **Tank size & options → Appearance: System / Light / Dark** allows an override. Phone backgrounds, cards, labels, native controls/dialogs and system-bar icons follow the selected mode. Android Auto continues using the host's own day/night rendering; phone appearance does not force Auto's mode.

## Phone nearby stations — version 0.19 / code 19

Phone home now has a visible **Nearby gas stations** section and **Find nearby gas stations** button. With search disabled, the button explains that current location is sent to public Overpass and asks to enable it. Precise location permission is requested if needed. Up to three nearest mapped stations appear with straight-line miles and navigation buttons opening an installed map app. Permission denial, missing fresh location, search progress, unavailable service, empty results and refresh rate limits are visible in the same section. OpenStreetMap attribution and unverified-hours/fuel warnings remain visible with results.

The existing nearby-search Options switch controls both phone and Auto using the same preference. Turning it off cancels phone location acquisition and pending result delivery and clears results. Phone search runs only while its activity is visible, with five-minute refresh and a one-minute network refresh limit. It uses the existing 5 km query, expanding to 25 km only if no stations were found; no fuel prices or road-distance/reachability guarantees are added. The Android Auto nearest-station row remains available and clickable for refresh/navigation.

## Phone fuel gauge — version 0.20 / code 20

Phone fuel bar is replaced by a semicircular E-to-F gauge, needle, quarter-scale ticks and percentage. Red/amber/green scale marks the first 10%, next 15% and remaining 75% of full-tank capacity. Gauge uses estimated gallons divided by capacity recorded at fill-up; it is not a fuel sensor or reserve measurement. Missing baseline shows No reading without a needle, rather than an empty-tank indication. Supports the selected phone light/dark palette, with a numeric gallons label and accessible description retained. The range bar and compact Auto bar images remain as before.

## Honda dashboard photo format — version 0.21 / code 21

The user's supplied dashboard shows AVG. CONS. 44.6 mpg and a lower-display odometer 187 beside TOTAL mile. The supplied recognized text includes the MPG value but separates it from its label, and omits the odometer digits entirely. Parser now recognizes singular Mile and permits a unique MPG value with explicit AVG. CONS. across separated OCR blocks. Multiple MPG values remain ambiguous. It never substitutes TRIP A 59.4 or average speed 29.0 for these readings.

If the original pass has no odometer, and a unique TOTAL label plus mile units are found, OCR retries a small crop left of that label, enlarged four times and contrast-enhanced in grayscale. Secondary OCR uses element positions: numeric candidates must be left of TOTAL at approximately the same height. Missing labels/digits or multiple candidates leave the field blank. The original photo stays visible and all readings still require review before saving. Show recognized text includes the second-pass crop result for troubleshooting.

The exact supplied text fixture now parses 44.6 MPG and leaves the absent odometer unavailable. Recognition of 187 from the photo on ML Kit must still be verified on the phone; the parser never hardcodes the sample reading.


## About — version 0.22 / code 22

About AfricaTwinTelemetry is available on phone home and in Options. It reads the installed package's version name and build code from PackageManager, so it shows the version actually running. Also shows the package name and a brief description. Debug source compilation passed; no signed bundle generated here.



## Options switches — version 0.23 / code 23

Bluetooth connection monitoring now has a saved on/off switch, respected by connection receivers and the monitor. Turning it off stops monitoring, without disabling the phone Bluetooth radio. Permission switches display actual precise/background location and tracking notification access, refreshed after permission dialogs or Settings changes. Android requires Settings to revoke permissions; the switches open it and retain the actual state until changed. Notification state includes the ride/change channels. The Options confirmation button is now Save. Debug source compilation passed; device permission-state/UI tests remain pending.


## Segmented LCD odometer — version 0.24 / code 24

The new screenshot confirms the enlarged ML Kit crop still recognized only TOTAL. Added a conservative seven-segment pixel decoder for the original image region immediately left of the uniquely located TOTAL label, with mile units still required. It applies adaptive grayscale thresholding, rejects border/noise/low contrast and ambiguous digit bands, and matches segment patterns. Currently requires 2–7 digits; a single-digit or uncertain reading stays manual. General OCR remains the other candidate source; disagreement between sources leaves odometer blank. The result is a suggestion requiring review, not a guaranteed reading.

A grayscale regression fixture sampled from the actual supplied photo (72×36 pixels surrounding 187) successfully decodes to 187. No hardcoded reading is used. The recognized-text dialog includes Segment-digit suggestion to identify whether the pixel fallback worked. Full automatic label positioning still needs the phone test on the same photo and additional dashboard photos.

## Stable Auto screens and station results — version 0.25 / code 25

Removed five-second invalidate loops from the Auto home and estimate detail screens. They now refresh on screen entry, an explicit tracking action or the header Refresh button. This trades automatic numeric updates while staying on a screen for stable scrolling; tap Refresh for current estimates. The app no longer forces periodic list redraws, though host-controlled navigation/rendering remains outside its control.

Both estimate screens have a permanent Nearby gas stations entry opening a dedicated station screen. Its header Search action and first Search nearby gas stations row request a new lookup. Up to three results have navigation actions, distance labels and OpenStreetMap attribution. This Auto controller has no periodic search refresh, and existing rows remain during a requested lookup until new results arrive. Permission/disabled/location/network status is shown rather than hiding the entry.

Phone and Auto share successful results through a private five-minute cache, so opening the Auto station screen can show a recent phone lookup. Distances are explicitly from the search location, not guaranteed current. Disabling search clears the shared cache. Expired results require a fresh search. Phone's existing foreground auto-refresh remains; no background network search introduced.


## Auto radial fuel gauge — version 0.26 / code 26

Auto fuel rows now use a compact semicircular E/F gauge image with colored scale, quarter ticks, needle and percentage. Rendered through supported Row image/CarIcon APIs on the API-8 host, with numeric fuel text retained. The range row retains its bar. Host controls image size; this is not a full-screen custom phone view. No timed redraw was reintroduced; use Refresh for current estimates. Debug source compilation passed; host gauge appearance remains unverified and requires bike/DHU testing. No signed bundle generated here.


## Trip-photo distance corrections — version 0.27 / code 27

Enable **Use Trip A/B photos to correct distance** in Options. Dashboard updates detect the visible Trip A or B and a unique miles reading in the photo. Before saving, the review requires confirmation that this trip meter was reset at the current fill-up. Its miles then replace the total since fill-up and clear manual offsets/gap warning, recalculating estimated fuel and range with the confirmed MPG. This can be done at any point after a full-fill baseline and does not double-count GPS distance. A reset during the fill-up interval invalidates this workflow: use TOTAL odometer or manual distance instead.

**Add missed miles → Read Trip A/B from a photo** opens this same review flow and enables the trip-photo option. A trip-only picture uses the saved average MPG if no MPG was recognized. Ambiguous A/B labels or multiple distance candidates stay unfilled. This correction does not invent a TOTAL odometer reading; the last confirmed TOTAL reading remains stored. Regular manual Add miles remains available for adding only the missed stretch.

Debug source compilation and regression checks passed for the supplied Trip A 59.4-mi OCR, Trip B, ambiguous A/B and metric rejection. Phone capture/review/save and trip reset accuracy remain device tests. No signed code-27 bundle generated here.

## Pause/resume during edits — version 0.28 / code 28

Fill up, Update dashboard reading and Add missed miles pause a running GPS tracker before opening their flow. Saving or canceling resumes tracking only if it was running at entry. Camera/picker cancel and launch failures also finish the pause; OCR failure offers manual entry while still paused. Switching from missed miles to a trip photo keeps the pause throughout. Validation errors keep the edit open and tracking paused.

Automatic-start attempts are blocked while the edit flag is active. Stop serial checks prevent restarting if a manual Stop or head-unit/projection disconnect occurred during editing. Previously stopped tracking remains stopped. Rotation retains restart intent and restores photo review or reopens the entry flow; unsaved manual field text may need re-entry. If the process is killed without restorable activity state, automatic resume is not promised.

Debug source compilation passed. Device verification remains needed for save/cancel, camera return, rotation, disconnect during edit and GPS continuity. No signed code-28 bundle generated here.

## Consolidated updates and station directions — version 0.29 / code 29

Removed the separate Add missed miles home button. Update dashboard reading now offers dashboard/trip photos, manual odometer/MPG and Add missed miles manually, sharing existing automatic GPS pause/resume. Full fill-up flow remains separate.

Phone and Auto station results show name (brand/operator fallback), straight-line miles, eight-point compass direction (N/NE/E/SE/S/SW/W/NW) and Tap to open map. Missing names use Gas station rather than Mapped gas station. Direction and distance are from the GPS search fix, not riding heading or routed distance, and recent cached results retain that origin. Refresh after moving for updated relative position. Same-position stations show Here; old cached entries without bearing show Direction unavailable until searched again.

Debug source compilation and regression checks passed, including cardinal directions, date-line eastward bearing and same-position behavior. Real station/map/UI tests remain pending; no signed code-29 bundle generated here.

## Live Auto compass — version 0.30 / code 30

Auto home has a Live compass entry opening a dedicated heading screen with compass image, cardinal direction and degrees. Only this screen updates, at most once per second and when a five-degree heading bin/source/availability changes; home/fuel scrolling remains free of timed redraws.

The existing GPS foreground service supplies fresh travel bearing while moving at least 2 m/s (~4.5 mph), with location accuracy <=25 m and bearing accuracy <=25 degrees when supplied. Travel direction is referenced to true north. If unavailable, a phone rotation-vector magnetic heading is used when sensor accuracy is medium/high. That heading refers to the phone's natural top-edge axis and magnetic north: place the phone top edge forward and avoid magnetic interference. It is not Honda vehicle telemetry or guaranteed motorcycle heading when the phone is in a pocket. Phone magnetometer availability/calibration varies.

GPS tracking must be running for either heading source. Sensor registration stays in the existing foreground service so screen-off sensor events can be delivered; no new permissions/service type added. Heading is process-local and cleared on stop, and values older than ten seconds are unavailable. This screen does not automatically start tracking or infer a stationary GPS direction.

Debug source compilation passed. Sensor accuracy, phone mounting/pocket behavior, GPS travel bearing, heading switch at stop, screen-off updates, host refresh/image rendering and compass interference all need real-device verification. No signed code-30 bundle generated here.

## Android Auto host sensor test — version 0.31 / code 31

On Auto home, open **GPS / compass test → Start GPS / compass test**. It subscribes to CarSensors.addCarHardwareLocationListener and addCompassListener at normal rate for up to 30 seconds. Status, callback counts and CarValue timestamps are displayed; the detailed latest report is saved privately. Precise phone location permission is required for the host location subscription. Compass is attempted separately even if location permission is absent. Leaving the screen or manual Stop removes subscriptions/timer. It does not start GPS tracking or use the test results in fuel/distance/compass estimates.

On the phone, open **Tank size & options → Saved GPS / compass test → Copy report** after the test completes. Report includes status/timestamps, returned lat/lon, provider, accuracy, location age, bearing, speed, mock flag and compass orientation vector. It contains precise coordinates: copying/sharing is a deliberate user action; no automatic network upload or route history is made. Each test replaces the previous report.

The installed projected library implementation was inspected and dispatches these requests through CarHardwareHostDispatcher. Host success proves an API response, not motorcycle hardware origin. Android Auto can provide phone-derived data; DHU can simulate sensors. No provenance field is assumed. Compare against phone orientation while parked (rotate phone with bike stationary) and repeat outdoor tests; matching phone coordinates alone cannot identify the hardware source. Do not disable the app's location permission to try distinguishing sources, because that also blocks the API.

Debug Kotlin source compilation passed. Real Honda callback behavior, listener cleanup, permission handling and source/provenance need device tests. No signed code-31 bundle generated here. Official API reference: https://developer.android.com/training/cars/apps/library/car-hardware-api

## Simultaneous host/phone GPS comparison — version 0.32 / code 32

Start GPS tracking first (automatic Honda tracking or manual phone Start). While parked outdoors with Auto connected, open **GPS / compass test → Start GPS comparison**, leave that screen open and wait 30 seconds. The existing ride foreground service publishes its actual GPS_PROVIDER callbacks to the test; the test independently subscribes to Android Auto's CarHardwareLocation API. It does not start or stop ride tracking or modify fuel totals. If phone tracking is not running, the test clearly reports missing phone fixes and no valid comparison.

The saved report keeps up to 60 samples per source with coordinates, provider, fix time, elapsed timestamp, receipt time, accuracy and mock flag. Each host sample is paired with its nearest phone fix time within two seconds; a phone sample may be reused. Summary shows pair count, min/mean/max coordinate separation, exact coordinate+time matches and maximum absolute fix-time difference. Pairing uses Location.time, not the unexplained CarValue timestamp. Compass status remains included.

After completion, open phone **Options → Saved GPS / compass test → Copy report**. The report includes precise locations and bounded short sample history, stored privately and replaced on the next run. No automatic upload. Matching streams or differing streams alone cannot prove which physical receiver supplied Android Auto's fix.

Debug source compilation and regression checks passed, including exact pair, missing phone data, stale/unmatched host time and known spatial separation. Real simultaneous callbacks, screen-off phone GPS and host sensor behavior still require bike tests. No signed code-32 bundle generated here.

## Preferred Auto location and station identification — version 0.33 / code 33

An active Auto app Session subscribes to host location, retaining subscription until session destruction. Ride tracking prefers successful host fixes with valid coordinates, accuracy <=25 m and elapsed age <=5 seconds. Phone GPS remains active as fallback and for comparisons; host location cannot be acquired before Android Auto starts/binds our Session, so automatic Bluetooth-started rides initially use phone GPS. Source is shown in tracking status and GPS heading. No physical Honda GPS provenance is asserted.

Only selected-source fixes feed distance and travel bearing. Switching sources clears the integration anchor and marks a possible gap instead of counting receiver-position offset as travel. Phone fixes continue to reach the diagnostic comparison even when not selected for distance. Latest valid host data is retained across temporary error callbacks only until its five-second expiry; disconnected session clears it.

Station name lookup now checks name/name:en, brand/brand:en, operator, network, official_name and short_name. A combined Overpass query also gets named convenience stores; a uniquely nearest store within 50 m can label an unnamed fuel site Fuel near [name], explicitly indicating association rather than verified station identity. Address is the next fallback, then Unnamed gas station. We cannot create missing OSM names. Old cached naming results are invalidated; run a new search.

Phone and Auto result rows show north-up arrows and compass letters (↑ N, ↗ NE, → E, etc.). They refer to the search location and geographic north, not a turn instruction relative to riding heading. Existing distance/map actions remain.

Debug source compilation and regression checks passed for source-anchor reset, additional name fields/fallback, direction arrows and existing math/OCR checks. Live Honda selection/fallback, foreground maps/session behavior, actual Overpass naming/nearby-store association and arrow glyph rendering need phone/head-unit tests. No signed code-33 bundle generated here.

## Tracking-session counters — version 0.34 / code 34

Each actual GPS Start resets session distance to zero and starts a monotonic elapsed timer. Stop freezes both totals; repeated Stops do not extend the timer. Phone tracking status displays current/last session miles and HH:MM:SS. Auto home header Session opens a dedicated live counter screen; estimate details also show a clickable session row. Only the dedicated screen refreshes when its display changes; home retains manual refresh and stable scrolling.

Session distance accumulates accepted selected-source GPS movement, separate from miles since fill-up. Photo/manual corrections do not inflate session GPS distance. Elapsed includes stationary time and waiting for a usable fix. Auto pause/resume for dashboard edits is a Stop/Start pair, so it starts a new session. After an unclean interruption, elapsed falls back to the last persisted heartbeat checkpoint rather than continuing indefinitely; startup after interruption creates a new session.

Debug source compilation passed. Real start/stop/pause transitions, duration persistence, source changes, GPS gap behavior and Auto live counter presentation need device tests. No signed code-34 bundle generated here.

## Single dashboard-update flow — version 0.35 / code 35

Removed the separate Add missed miles choice/dialog. Update dashboard reading now offers photo capture, an existing photo, or manual missed distance plus MPG. In manual mode, enter only additional untracked miles; 0 applies an MPG-only update. The untracked stretch is added to the existing total and the new MPG recalculates fuel/range. The preview explicitly shows this meaning, and gap warnings remain because an added stretch does not prove every gap is covered.

Photos continue to correct total distance from confirmed TOTAL odometer or enabled Trip A/B mode (with fill-up-reset confirmation), replacing the total instead of adding it twice. A failed photo read offers manual additional-distance/MPG entry with explicit labels. Both paths pause/resume GPS as before. Fill-up behavior unchanged.

Debug source compilation passed. Manual additional miles/zero-distance economy-only update, photo corrections, cancel/resume and real-device UI need testing. No signed code-35 bundle generated here.


## Compact Auto home (0.39)
Home now has three rows: combined estimated fuel/range with fuel gauge; tracking toggle with session miles/time and heading; and nearby stations. More contains session, compass/GPS diagnostics, and estimate details. Configuration remains on the phone. Actual row sizing is controlled by Android Auto, so the Honda display must be checked for fit. Kotlin compilation and existing domain checks passed; no new signed bundle was generated.




## Periodic fuel notifications (0.41)
Enabled by default while GPS tracking runs, every 10 minutes; switch off or choose 5/10/15/30 minutes in Tank size & options. Test fuel & range notification sends one immediately. Uses CarAppExtender and CarNotificationManager with car intent opening FuelStopService; phone intent opens MainActivity. Updates a single notification ID and cancels on tracking stop. Car importance HIGH requests a heads-up notification; actual Honda dashboard placement must be tested. Notification permission must be granted. No navigation or messaging category is impersonated. Source compilation and domain checks passed; no signed bundle or live head-unit test was performed.



## Combined head-unit summary (0.42)
Session distance and elapsed time now appear in the same home-screen row as the fuel gauge, estimated fuel and range. Tracking controls show status and heading. Periodic notifications continue to show only fuel and range. Source compilation and domain checks passed; bike rendering requires testing.



Version 0.43: the combined Auto summary is labeled Current session. It displays the current tracking-session counters, resets at Start, and retains those totals on Stop until the next Start. The separate tracking control reports active/stopped state.



## Tracking button and compass (0.44)
Auto home uses PaneTemplate with two read-only rows (combined fuel/range/session and a compass/status row) and two actual body buttons (tracking and stations). Tracking is the sole primary action: green Stop GPS ON when active, blue Start GPS OFF when stopped. Start retains the parked-only phone start flow. Compass uses a square card with cardinal labels and a needle; unavailable heading shows a dash instead of a fabricated direction. Host controls exact image/button positioning and color rendering, so adjacency is not guaranteed. The two-row pane refreshes live while visible; More retains details and diagnostics. No API-9 features are used. Pane constraints checked against official documentation and installed library: no row click listeners/toggles, at most two body actions and one primary, only one titled header action. Kotlin source compilation and domain checks passed; Honda layout is untested and no signed bundle was produced.



## Simplified menus (0.45)
Removed redundant fuel estimate details and standalone live compass pages. The home pane retains fuel/range/session and compass. Auto More contains Session details, Settings (phone configuration guidance), and Advanced. Auto Advanced contains GPS comparison and notification testing. Phone Settings contains Advanced for notification testing and saved head-unit/GPS reports. Kotlin compilation and domain checks passed; no signed bundle was generated.



Version 0.46: stations button renamed Find gas. Fuel arc, ticks, needle and labels enlarged within the existing large row image; compass dial, needle and labels enlarged in its square card. Both retain IMAGE_TYPE_LARGE: the host controls actual image-slot dimensions. Kotlin source compilation passed; Honda display fit requires testing.



## Ride improvements (0.47)
- Low estimated range alert: enabled by default, 40 mi threshold; Settings offers 20/40/60/80 mi and a disable switch. Checks during tracking, once per full fill-up, including across tracking restarts. Fill-up clears the old alert and rearms it. Separate from periodic summaries. Notification permission and Android Auto host behavior still apply.
- Tracking health on phone and Auto: stopped, interrupted, waiting for reliable GPS, GPS ready awaiting movement, or recording. Valid GPS reception is tracked separately from accepted distance so stationary time is not treated as missing reception.
- Undo last dashboard update/fill-up on phone: one persistent snapshot of baseline, current odo, MPG, filled capacity and corrected ride distance. Undo pauses/resumes tracking through the existing edit flow, keeps GPS distance recorded after the change, preserves session counters and settings, and then consumes the snapshot. The latest correction replaces the previous Undo. Confirmation survives rotation. Prior tracking gaps remain visible.
- Station results now survive beyond five minutes and remain available when lookup fails. Both screens show search date/time and age; results over five minutes are marked saved. Distances/directions refer to the original search position. Disabling station search still clears cached results. A fresh successful search replaces them.
- Ride summary saved on each tracking Stop: distance, elapsed time, estimated fuel used from accepted GPS distance divided by confirmed MPG. Phone Last ride summary and Auto More → Session details show the saved summary. Optional Stop notification is enabled by default. Dashboard-edit pauses count as Stop/Start boundaries. Fuel use is an estimate and excludes idling, untracked/manual distance and changing conditions; notification placement is host controlled.

Verification: full Kotlin source compilation passed, along with new tests for Undo retaining post-change GPS distance, full-fill undo, invalid distance rejection, low-range threshold boundaries, disabled/latching states and missing/invalid range. Existing distance/fuel math, station, GPS comparison, OCR/parser and actual-photo segment checks also passed. No signed APK/AAB was generated, and Android phone/Honda rendering and notification behavior require device testing.


## Guided dashboard camera (0.48)
Take dashboard photo now opens an in-app Camera2 preview. Align the upper-left MPG/trip panel with the cyan box and lower TOTAL-mile display with the yellow box. Guides are approximate framing aids based on the supplied dashboard photo, not OCR crop boundaries. They are not burned into the captured JPEG; existing full-image OCR, lower-display second pass and review-before-save remain unchanged.
The capture requests autofocus where supported, allows a short focus settling delay, captures without flash, and stores a high-quality JPEG in the existing private cache. Preview maintains camera aspect ratio and compensates for display rotation; JPEG orientation follows sensor/display orientation. The guided activity respects system-bar insets. Use phone camera instead is retained as a fallback, and choose-existing-photo still needs no camera permission.
This adds runtime CAMERA permission. With CAMERA declared, Android also requires it for the external-camera fallback. Grant it when prompted or cancel and choose an existing image. Camera hardware/autofocus features are optional in the manifest. GPS pause/resume and photo confirmation behavior remain in the existing flow.
Verification: Kotlin source compilation and all domain checks passed. Live camera preview, focus, sensor orientation, fallback behavior and precise guide alignment have not been tested on the phone. No signed APK/AAB was generated.


## Always-available station search (0.49)
Removed the search enable switch and enable-confirmation dialog. Search availability now follows precise-location permission, and prior disabled preferences are ignored. Phone foreground refresh and manual Auto search remain; permissions are checked before requests and before accepting responses. Settings retains the notice that station searches send location to Overpass/OpenStreetMap. Cached results remain available across lookup failures. Kotlin compilation and existing domain checks passed; no signed bundle generated.



## Station cooldown display (0.50)
Removed the persistent Wait a minute message. Phone Find gas and Auto station-screen Search actions disable while searching or cooling down, show remaining seconds, and re-enable automatically. The countdown changes once per second while visible; station results and lookup status remain available. The one-minute request timestamp is shared across phone and Auto controllers in the process. Re-entering the screen during cooldown no longer replaces result status with a cooldown message. Queued location callbacks are prevented from starting duplicate lookups during an active query. Kotlin compilation and domain checks passed; host disabled-button rendering requires device testing.



## Station failure diagnostics (0.51)
Search now distinguishes network timeout, DNS/connectivity failure, TLS failure, HTTP rate limiting/denial/busy responses, oversized responses, invalid JSON and incomplete Overpass responses. Saved results remain visible. Settings → Advanced → Station search report shows and copies the latest request timestamp, service, HTTP status, radius, elapsed duration, received bytes, and classified outcome. Coordinates, raw query/response and arbitrary exception messages are excluded. User-Agent uses installed app version. Kotlin compilation and existing/new error-classification checks passed. Actual recurring failure reason remains unverified until the new report is captured on the phone; no signed bundle generated.



## Fuel-triggered automatic search (0.52)
Automatic station lookup only runs at or below the configured estimated fuel percentage; default 10%, adjustable as a whole percentage 1–100 in Settings → Auto-find gas. Uses remaining fuel divided by the capacity recorded at the last full fill-up. Missing/invalid estimates never trigger automatic search. Checks every 10 seconds while the phone app is open, attempts no more often than every five minutes, and keeps existing location permission and request cooldown rules. Manual Find gas works at all fuel levels. Auto head-unit station screen remains manual and uses the shared cached results. Kotlin compilation and tests passed, including exact threshold, above/below threshold, configurable percentage and missing/invalid fuel estimates. No signed bundle generated.



## Busy-server retries (0.53)
HTTP 502/503/504 and explicit server timeout remarks retry up to five times after the initial attempt. Waits default to five seconds and honor a longer Retry-After seconds/date header. Rate limiting (429), denial, invalid JSON, oversized responses, DNS/connectivity and generic client timeouts do not use rapid retries. Retry budget is shared across the 5 km and 25 km lookup stages. UI shows retry number and remains disabled while searching. Leaving/stopping the controller cancels pending work/waits and prevents further retries; in-flight network I/O may take its timeout to finish. Cached results remain visible. Retried results refer to the original search location and freshness timestamp, so movement during a search does not imply current-position distances. Diagnostics record attempt count. Source compilation and retry-boundary/error-type checks passed; no signed bundle generated.



## Large Auto dashboard tiles (0.54)
API-8 home now uses GridTemplate with two large square tiles: fuel/range/current session and compass/tracking health. ITEM_SIZE_LARGE requests larger image areas and wider tiles; actual column count and dimensions remain host controlled. The fuel image includes the radial gauge, fuel/range values and session distance/time, with matching accessible title/text. Compass renders at 512×512. Tap the fuel tile for session details. Find gas remains a titled header button; the tools icon opens More/Settings/Advanced. A green pause-style floating button stops GPS, and a blue play-style button starts through the existing parked-only phone flow. Floating buttons have icons/backgrounds and no titles/primary flag, matching GridTemplate FAB constraints. API levels below 7 use a Pane fallback; API 7 uses standard grid image sizing. Kotlin source compilation and domain checks passed. Exact Honda grid sizing, fit and color rendering require device testing; no signed bundle was produced.


## Crash recovery diagnostics (0.55)
User reported startup crashes affecting phone and Auto after the grid update. Exact cause remains unconfirmed: no device stack trace is available and local ADB cannot connect to its daemon in this environment. A custom Application records uncaught exceptions synchronously then delegates to Android's existing crash handler. The next phone launch opens a minimal recovery screen with Copy error report and Continue to app. Advanced also offers Copy saved app error. No fuel/ride preferences are erased.
The Auto dashboard catches RuntimeException during local template construction, records it with host API level, and substitutes a simple Pane with tracking and station controls. This does not catch errors occurring inside Android Auto's own renderer. The grid remains enabled until an actual exception occurs. Compilation and domain checks pass; no root-cause fix, signed build, or live recovery test is claimed. After installing, reproduce once and paste the saved report to identify the cause.


## Confirmed grid crash fix (0.56)
The saved API-8 report identified a primary action passed to GridTemplate.addAction, whose FAB constraints allow zero primary actions. Action.Builder.setFlags performs bitwise OR: setFlags(0) did not remove FLAG_PRIMARY from the copied pane action. The grid tracking action is now built fresh with icon/background/click listener and no title or primary flag; pane controls retain their primary flag. A JVM regression check against the installed Car App Library reproduced the rejected copied action and validated the fresh non-primary action. Kotlin compilation and domain checks also passed. Crash diagnostics remain available. Honda rendering still requires retesting; no signed bundle generated.



## Graphic-only dashboard tiles (0.57)
Fuel and compass tiles omit external title/text, preserving all existing text drawn inside their graphics. Fuel/range/session and heading/tracking health appear in two separate information tiles using standard info icons (GridItem requires an image). ITEM_SIZE_LARGE remains requested. Host controls cell/image dimensions and grid wrapping; full-cell image expansion is not guaranteed, and the four-tile layout may require scrolling. Kotlin source compilation passed; live Honda rendering is untested. No signed bundle generated.



## Combined pane dashboard (0.58)
Replaced home grid with PaneTemplate and its dedicated pane image (API 4+). One 1024×512 bitmap combines existing fuel/session graphic and compass side by side, preserving their embedded text. Native fuel/range/session and heading/health remain alongside for accessibility/readability. Tracking is a titled pane button with its supported primary flag and state color; header retains Find gas and tools. Pane rows have no click listeners or toggles, and there is only one primary body action. Host controls actual pane image dimensions; greater displayed size is an experiment requiring Honda/DHU testing. Source compilation passed, no signed bundle generated.



## Square pane image (0.59)
Screenshot showed the host center-cropping the wide 2:1 pane image into a square. Dashboard composition is now 512×512 with both complete gauges in the upper area and shared fuel/range/session/heading text below. Removed duplicate tiny embedded fuel-card captions by redrawing the same information across the full panel width, retaining gauge E/F and percent markings and compass letters. Session row wording shortened to avoid the separate elapsed line. Source compilation passed; actual Honda fit remains to be checked. No signed bundle generated.



## Separate visible gauge panels (0.60)
Both gauges remain visible simultaneously in distinct stacked bordered panels inside the single square pane image. Fuel graphic width increases from 240 to 336 image pixels; compass diameter from 240 to 276. Gauge E/F/percent and compass cardinal labels remain. Fuel/range/session and heading/health readings stay in native text alongside, rather than duplicated across the lower image area. This uses one host image slot, not two independently sized Android Auto panes. Source compilation passed; live fit/display size must be tested. No signed bundle generated.



Version 0.61: the upper image panel splits into equal halves: radial fuel gauge on the left, estimated fuel in US gallons and range in miles on the right. The lower compass panel retains its size. Native session and other readings remain alongside. Source compilation passed; device rendering still requires testing.



Version 0.62 corrects panel layout: upper half of the square contains the centered fuel gauge followed by fuel and range text below it; lower half contains only the compass. No side-by-side fuel/text split. Source compilation passed; head-unit rendering remains to be checked.



Version 0.63: phone and Auto home current-session counters display zero when tracking is stopped/interrupted instead of showing stored completed-session time (the reported 39 seconds). Start already resets session counters in RideStore.start. Completed totals remain in Last ride summary and session details. Source compilation passed; live start/stop display still requires checking.



Version 0.64 adds current cardinal direction and bearing (for example W 248 degrees) inside the compass graphic, with a contrasting label backing. Missing/stale heading shows a dash. Native heading text remains for accessibility. Source compilation passed; actual head-unit appearance requires testing.



## Direct head-unit GPS Start (0.65)
Manual parked-only head-unit Start now requests RideTrackingService directly when precise and background location (Android 10+) are granted, without requiring Honda automatic tracking to be enabled. Missing permissions or synchronous Android service-start rejection fall back to launching the phone activity using application context. Recorded fill-up is still required; editing must finish first. Start request is distinguished from service confirmation, and asynchronous service startup failures are reported in live status.
Settings → Advanced → GPS start report shows and copies permission checks, direct-start requests, phone fallback results, and service success/failure stack traces. The latest 16 KB history is stored privately. Handled startup failures do not trigger crash recovery. Android foreground-service restrictions still apply, so device testing is necessary. Kotlin source compilation and existing domain checks passed; no signed bundle generated.


## Heading and GPS speed beside compass (0.66)
Compass dial moves left within the lower panel; heading and GPS speed in mph appear on the right, outside the dial. Speed uses the active tracker location source (fresh Android Auto GPS or phone fallback), not Honda vehicle telemetry. Requires a valid recent fix with speed, finite 0–90 m/s, and speed accuracy at most 3 m/s when supplied. Values expire after 10 seconds and clear on tracking Start/Stop. Missing/invalid speed displays a dash; true measured zero displays 0 mph. Native text also includes GPS speed for accessibility. Source compilation and domain tests passed, including mph conversion and invalid values. No signed bundle generated; device speed/display testing remains necessary.



## Heading-relative station arrows (0.67)
Station arrows now rotate relative to current heading: south-facing with a northwest station produces down-right. Compass letters still describe the absolute station bearing. Phone and Auto lists update their displayed arrows as heading changes. Fresh active-tracker coordinates update straight-line distance and station bearing without another network lookup; without fresh coordinates, saved search-position bearings are used and labeled. Missing heading explicitly falls back to north-up arrows. Magnetic phone headings are converted to true north using local declination when a fresh position exists; otherwise no magnetic/true-north mixing is attempted. This still does not prove motorcycle GPS origin.
Exact station bearing is saved for new cached searches; older cached cardinal bearings are approximated only when live position is unavailable. Tests cover south-to-northwest, straight-ahead, absent heading, wraparound and right-side directions. Kotlin compilation and all domain checks passed; no signed bundle generated.


## Location fallback (0.68)
Station search and station distance/arrows use fresh coordinates when available, otherwise the newest valid saved/Android last-known fix, otherwise Hell, Michigan (42.434722, -83.985). Default coordinates are based on https://www.wikidata.org/wiki/Q1602605. A fresh tracker fix or recent accurate provider fix starts searching immediately. Otherwise the app waits up to 20 seconds for a provider fix, then uses fallback; disabled providers use fallback immediately. Precise-location permission is still required for search.
Real coordinates are saved privately across app restarts. The Hell default is never saved as a real GPS fix. Results identify Last known location with age, or Hell, Michigan (default; no GPS location), including after caching. These fallbacks do not advance ride distance, supply speed, or pretend GPS tracking has a signal. Automatic fuel thresholds, station cooldown and busy-server retries remain in place. Kotlin compilation and existing domain checks passed; no signed bundle or device fallback test performed.

