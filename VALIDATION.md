## Version 0.25 / code 25 — remove Auto periodic redraws and restore station visibility

Debug Kotlin source compilation and executable existing regression checks passed. Removed timed estimate-screen redraws; explicit Refresh/onStart/tracking actions remain. Added dedicated Auto station screen reachable from home and detail, with search action/status and up to three navigation rows. Shared five-minute result cache makes phone results available on subsequent Auto station-screen entry. Auto search controller disables scheduled refresh and retains found rows during new lookup; callbacks refresh only for search events.

Pending device tests: scroll remains stable over several minutes; manual Refresh; enter station screen from home/details; show recent phone lookup; search via Auto; blocked precise location; results/cooldown; navigation and attribution. No live Overpass or head-unit UI test performed here. Source checks cannot guarantee host scroll behavior. No signed code-25 bundle produced/uploaded.

## Version 0.24 / code 24 — segmented odometer fallback

Debug source compilation and executable checks passed. Actual grayscale pixels from the user-supplied 187 odometer region are a regression fixture and decode to 187; a uniform low-contrast region returns null. Existing dashboard text, station, distance and fuel regressions passed. Added bounded TOTAL-left pixel crop and segment matching, with fill/aspect/row-band/digit-count safeguards and explicit ambiguity if OCR disagrees.

The fixture verifies digit recognition on the actual image, not Android ML Kit's label bounding box selection. Full photo-to-field flow, crop positioning under rotation/perspective and other readings still require phone tests. No signed bundle generated or uploaded here.

## Version 0.21 / code 21 — supplied Honda OCR failure

Debug source compilation and executable regressions passed. Added exact supplied recognized-text fixture: 44.6 average MPG resolves with distant AVG. CONS. label, missing 187 remains null. Additional checks cover singular TOTAL Mile, cropped 187, multiple cropped numbers, MPH contamination and ambiguous MPG. Existing parser/trip/instant/metric safeguards and fuel/distance/station checks pass.

Added anchored enlarged/contrast-enhanced second ML Kit pass with spatial filtering left of TOTAL. Crop results included in recognized-text dialog; original preview retained. No photo uploads or new external service introduced. Real ML Kit recognition of the supplied photograph and UI prefill cannot be executed in this desktop source-check environment and remain pending on the phone. No signed code-21 bundle generated/uploaded here.

## Version 0.20 / code 20 — semicircular phone fuel gauge

Debug source compilation passed. Added a native Canvas view with E/F scale, fixed colored zones, quarter ticks, needle angle from bounded finite estimated fuel fraction and explicit unknown state. Native layout respects existing screen insets and appearance settings. Numeric gallons/accessibility description retained. No new dependency or calculation changes.

Visual device verification remains pending for small screens, light/dark, full/half/empty/unknown and large text. No signed bundle generated or uploaded here.

## Version 0.19 / code 19 — visible nearby station feature on phone

Debug source compilation and existing nearest-station/distance/fuel/parser regression checks passed. Shared FuelSearchController now accepts a general Context with either car lifecycle callbacks or explicit phone start/stop/close. Phone home renders progress/errors, station list, navigation actions and attribution; consent/precise-permission flow is reachable from Find nearby gas stations. Disabling cancels location requests, invalidates in-flight results and clears visible phone stations. Leaving the phone activity stops search; destroying it releases the worker.

Pending real-device/network tests: opt-in, denied/granted precise location, outdoor GPS, Overpass response/failure, three results, map navigation, toggle-off during lookup, light/dark layout and Auto results. No live lookup was executed here. No signed code-19 bundle produced/uploaded; build in Studio.

## Version 0.18 / code 18 — bike Bluetooth/projection monitor and phone dark mode

Debug/release Kotlin source compilation passed; executable regressions passed including Bluetooth name suffix/case matching and rejection of unrelated/null names. Final debug check passed after stop suppression and connection-observer ordering changes. Manifest receiver only accepts protected Bluetooth ACL actions and checks runtime permission before name access; monitoring service is nonexported. Added connectedDevice FGS permission/type and BLUETOOTH_CONNECT, retaining existing location/background/notification permissions.

Connection monitor observes CarConnection until projection disconnect or bike disconnection before projection. Live profile seeding supports enabling with an already-connected bike. Lifecycle cleanup removes observer, profile proxies and pending callbacks; late profile callbacks do not start GPS after service destruction. Startup is guarded and failures surfaced. Standby monitoring does not consume GPS until both conditions match. Old session-end stop remains available.

Phone appearance applies native Material light/dark theme before activity creation and selects palette/icon contrast while retaining screen insets. Default follows system, with saved override. No device screenshots/UI control available here; phone dark mode, keyboard/dialog contrast and Bluetooth/Auto startup require real-device verification.

Pending critical tests: cold/locked phone; connect Honda Bluetooth without opening Auto app; wait for projection then GPS; non-bike Bluetooth; Android Auto car connection; permissions denied; manual/notification Stop; rapid reconnect; projection loss; force-stop; HEADSET/A2DP profile availability and delayed notification. OS background FGS exemptions are not proven by source checks. No signed code-18 bundle produced/uploaded; full Gradle build remains blocked in this session.

## Version 0.16 / code 16 — phone system-bar overlap fix

Debug Kotlin source compilation passed. Main ScrollView applies fresh platform window insets for system bars, display cutouts and keyboard (API 30+), with legacy system-window/cutout fallback on Android 9/10. Scroll drawing clips to the safe viewport. Explicit edge-to-edge setup keeps inset ownership consistent; light-system-bar appearance matches the pale background. No test added for this reversible layout adjustment.

Pending device visual checks: top heading below clock/status icons; last Options button above three-button/gesture navigation; portrait/landscape and cutout; keyboard/dialog inputs. Not visually verified on a device here. No signed bundle generated/uploaded.

## Version 0.15 / code 15 — state toggle and disconnect notices

Debug Kotlin source compilation passed. Inspected installed library bytecode to confirm host unbind destroys the associated session lifecycle. Disconnect behavior is implemented on final recognized Honda session destruction, with CarAppService destruction fallback; onStop/screen changes do not trigger stopping. Unknown/other manufacturers do not register Honda sessions. Stop control and disconnect share RideControls.stop; the running service reports actual start/stop transitions for delayed notification.

Notification transition messages target eight seconds, cancel superseded pending messages and recheck current state and POST_NOTIFICATIONS. Existing immediate foreground notification retained. Delayed notices are process-local and best-effort, not guaranteed after process termination.

Pending real-device tests: enable auto/permissions; connect/reconnect Honda; hide app/change screens; disconnect; toggle twice; deny notification; stop within eight seconds; verify GPS acquisition and notification delay. Host session destruction may happen for reasons other than physical disconnect and must be validated. No signed bundle generated or uploaded.

## Version 0.14 / code 14 — Honda_Wing automatic-start attempt

Debug source compilation and executable regression checks passed, including exact manufacturer matching, rejecting unsuccessful/null/other/case-different values. Automatic-start guards cover enabled preference, full-fill baseline, existing tracking, precise/background location and active session; one attempt per Session prevents repeated restarts after manual Stop. Start exceptions are caught and surfaced. Actual service/GPS status remains separate from start-request success.

Added opt-in phone controls and background-location setup with explanation, separate foreground/background grants and Android 11+ app-settings flow. Official Android background-location and foreground-service restrictions checked. Background permission alone does not guarantee foreground-service startup eligibility.

Pending: real Honda automatic startup (phone closed/locked), permission denial, blocked FGS start, manual Stop/session restart, other vehicles and Play background-location declaration/review. Source compilation does not validate those runtime behaviors. No signed bundle generated/uploaded here.

## Version 0.13 / code 13 — startup host/vehicle diagnostic history

Debug Kotlin source compilation passed using the installed SDK/cached dependencies. Public signatures for CarAppService.getHostInfo, CarInfo.fetchModel and Model/CarValue were inspected; projected model query permission requirements verified against official Android documentation.

Session onStart captures local host/app/phone version info and fetches vehicle identity. History retains 20 independent records with per-query statuses, timestamps, timeout or failure. Callbacks update their own record by ID, so late responses cannot overwrite the newest session. Report is available offline in phone Options with selectable text, Refresh and Copy. No fuel/speed/odometer query, new vehicle-data permission, network upload, phone boot receiver, hardware firmware claim or automatic bike recognition is introduced.

Pending device tests: launch app on Honda; wait for model callback or timeout; disconnect then read copied report offline; compare another head unit/DHU; reopen to confirm a separate record; confirm update persistence and 20-record retention. Android runtime persistence and real-host responses were not executed here. No signed code-13 bundle generated or uploaded.

## Version 0.12 / code 12 — dashboard reading confirmation

Added an estimate preview before saving, explicit field labels, Save readings button, successful-save feedback and persistent display of saved odometer/MPG/baseline. Rechecks active tracking at save time. No confirmed root cause for the user's reported unchanged estimate yet; photo recognition still requires reviewed confirmation and full fill-up intentionally resets to a full tank.

Debug Kotlin source compilation and existing parser, distance and fuel regression checks passed. Photo capture/review/save/persistence still requires real-device reproduction. No signed bundle generated here.

## Version 0.11 / code 11 — estimated fuel and range bars

Added phone fuel/range bars, numeric values, full-scale labels and accessible descriptions. Fuel colors change at 25%/10% remaining. Added bitmap bar images via CarIcon/Row on Auto main and estimate detail screens; no API-9 components are used. Unknown data stays explicitly unavailable rather than implying an empty tank. Both scales use the recorded full-tank capacity and current saved MPG.

Debug Kotlin source compilation passed. Visual layout and host image rendering still need phone/DHU/bike verification. Full signed bundle build/upload remains pending in Studio.

## Version 0.10 / code 10 — manual missed miles

Added a phone home-screen adjustment with positive finite input validation and a live preview of additional estimated fuel consumption, new total miles, remaining gallons and range. Saving requires an existing full-fill baseline and stopped tracking. It adds to existing recorded miles without altering the confirmed odometer. A later dashboard reading replaces total miles; a new fill-up resets them. Gap warnings are retained.

Debug source compilation and executable regression checks passed, including cumulative offsets, invalid inputs, fuel/range after an offset and depletion clamped to zero. Android UI/persistence and real-device tests remain pending. No signed code-10 bundle was generated or uploaded here.

## Version 0.9 / code 9 — prominent GPS controls

Debug and release Kotlin source compilation passed against the installed Android SDK and cached AndroidX/ML Kit dependencies. Executable distance/filtering, fuel arithmetic, nearest-station and dashboard-parser regression checks passed. This is direct source compilation, not a full Gradle bundle build or UI/device test.

Phone home now exposes Start/Stop and tracking status; location permission is requested from Start when necessary. Auto home and details put the shared control first. Auto Start is parked-only and hands off to a visible phone activity for location-service startup. Auto Stop stops the existing service directly. Main Auto list remains at most six rows and shows the single nearest station to make room for the control.

Pending: signed bundle generation in Studio; phone/DHU/bike verification of the start handoff, permission denial/retry, Stop synchronization, GPS acquisition and screen-off distance recording. Version 9 has not been signed or uploaded here.

# Validation — October 12, 2026

## Versions 7–8 UI cleanup and dashboard-photo capture (current)

Removed telemetry queries, diagnostics screens, telemetry tests and CAR_FUEL/CAR_SPEED/CAR_MILEAGE permissions. Replaced phone UI with fuel/range card, Fill up, Update dashboard reading and Options. Added persistent odometer baseline, configurable 5-gallon default capacity snapshot, confirmed MPG recalibration and GPS distance correction. Android Auto now shows estimates and station lookup. Debug/release source compilation for cleanup passed.

Version 8 adds external full-resolution camera capture, gallery/document selection, narrow FileProvider cache sharing and bundled on-device ML Kit OCR. Every suggested reading goes through preview/edit/confirmation; ambiguous labels/values and unsupported units require manual correction. Pure OCR-parser, distance/GPS and fuel-math executable checks passed. Final version-8 Android build/lint and image/camera/lifecycle/device tests are pending because ML Kit dependencies are not cached here. No completed signed bundle, ML Kit runtime accuracy or camera validation is claimed.

## Version 5 initial GPS ride tracker (current)

Added a non-exported user-started location foreground service with persistent notification/Stop action, GPS distance filtering, persistent totals, gap reporting, phone baseline/MPG and distance-correction controls, and a live Android Auto estimate screen. Increased code to 5/name to 0.5. Main car screen shows up to two station results to stay within six rows when both diagnostics and GPS summary are present. Debug/release source compilation and executable GPS/fuel/status tests pass. Full Gradle APK/AAB assembly/lint and on-device foreground/screen-off/lifecycle/ride tests are not completed. Source checks use cached generated resources; Studio must rebuild the actual app.

## Version 4 vehicle diagnostics (current)

Added fetchModel, fetchEnergyProfile, speed and mileage listeners on a separate six-row diagnostics screen. Declared/requested projected CAR_SPEED and CAR_MILEAGE permissions. Queries fail independently and ignore late static callbacks after stop; dynamic listeners clean up on stop. Displays explicit statuses, validated finite/nonnegative values and dynamic freshness. Numeric speed/odometer conversions use documented base units. Identity/fuel-type codes are logged without asserting Honda support. Debug/release source compilation and executable status checks pass. Full signed AAB build/lint and hardware permission/query tests remain pending; update via Play code 4 is required.

## Version 3 fuel-feature packaging fix (current)

Moved the actual probe to main sources and CAR_FUEL permission to the main manifest; removed the release no-op and bikeTest source redirection. All variants now contain the fuel UI/probe. Bumped versionCode to 3/versionName to 0.3. Debug/release source checks and fuel-display checks pass. A new signed AAB and Play update are required; full Gradle build/lint and actual fuel availability remain pending. Earlier statements about release excluding fuel refer to previous versions.

## Version 2 host-validation fix

User-provided real-head-unit logs show `CarApp.Val: Unrecognized host` for Android Auto. The observed package/certificate pair was independently matched against the cached official androidx.car.app:app:1.8.0-rc01 AAR's hosts_allowlist_sample. Added that exact pair to non-debuggable HostValidator configuration, preserving package and certificate validation. Incremented versionCode to 2/versionName to 0.2 for a Play update. Debug/release source checks pass; a newly signed bikeTest bundle, Play update and real-head-unit retest are still required. Fuel availability remains unverified.

## Private bike-test build preparation

Added a non-debuggable `bikeTest` build type that inherits release configuration and includes the fuel probe source/permission. Fuel display and permission UI use probe availability rather than debuggability; host validation still uses debuggability. Existing debug/release source checks and executable checks pass. New build-type Gradle wiring, signed AAB generation and Play installation await Android Studio sync/build and user signing credentials.

## Debug fuel-display follow-up

Added a debug-only host fuel percentage row with permission, host API, error-status, timeout and stale-reading states. Percentage validation tests cover valid zero, valid 50%, missing/NaN/out-of-bounds values and stale/future timestamps. Debug/release source compilation passes; full APK/lint and host runtime checks remain pending. DHU percentages are simulated, not Honda measurements.

## Nearest-station follow-up (current)

Removed the Google service and price/routing integration, companion server, URL/key configuration and debug cleartext exception. OpenStreetMap lookup now runs directly from the app, ranks mapped stations by straight-line distance, and requires only search opt-in, precise location and internet. Range is logged separately and is not used to claim reachability. Debug/release source compilation and executable nearest-station checks pass. Full Gradle build/lint, live lookup and phone/host validation remain pending. Earlier APKs are stale.

## Automatic fuel search follow-up

- Six offline Node tests passed for price ranking, reserve/road-distance filtering, missing/failed routes, stale/future/wrong-grade/missing prices, invalid inputs and mocked Google integration.
- Server syntax check passed.
- Debug and release Kotlin source compilation checked directly against cached dependencies and the installed SDK; passed. This is not full Gradle APK assembly or lint.
- Added location and internet permissions, debug loopback-only cleartext policy, phone configuration, car result display and automatic foreground refresh.
- Live provider calls, phone UI, complete Android build/lint and hardware validation remain pending. No API key or billed service has been configured.
- Existing APKs and the historical results below predate this feature. Rebuild in Android Studio.

## Earlier prototype checks

Follow-up change: added a debug-only fictional Demo Fuel Station fallback and updated phone instructions. The fallback has no navigation action and does not overwrite saved stops. This follow-up was inspected in source but has not been rebuilt; build results below refer to the preceding version.

Final configuration: AGP 9.4.0, Gradle 9.6.0, built-in Kotlin, Car App Library 1.8.0-rc01, installed Android platform 37.0, Build Tools 36.0.0, Android Studio bundled JDK 25.0.3.

- `:app:assembleDebug`: passed; debug APK produced.
- `:app:assembleRelease`: passed; unsigned release APK produced.
- `:app:lintDebug`: passed with zero errors. Remaining warnings concern prototype English strings, backup configuration, exported car service validated through HostValidator, and a newer optional Gradle version. Compilation also warns about legacy ListTemplate header setters retained for API 3 compatibility.
- Merged manifests: CAR_FUEL occurs only in debug, not release.
- Release source has a no-op probe; real energy subscription exists only in debug source.
- Git repository initialized; no remote or upload configured.
- Android device bridge: no connected devices.
- DHU package absent from installed SDK. Simulator and Honda testing are pending; neither runtime behavior nor actual motorcycle data is claimed as validated.

The build and lint checks do not establish Play category approval, reliable telemetry, or hardware compatibility. See README for test procedure and source references.















## Version 0.36 — Auto launch correction
The main ListTemplate action strip previously contained two custom titles (Refresh and Session). Inspection of the installed Car App Library 1.8.0-rc01 confirmed ListTemplate uses ACTIONS_CONSTRAINTS_SIMPLE, whose maximum custom-title count is one. Refresh is now an icon-only action; Session remains the sole titled action. Kotlin source compilation and existing domain checks passed. No signed bundle was produced and the Honda head unit has not been retested; the screenshot alone does not supply a crash stack trace.


## Version 0.47
Kotlin source compilation and all existing domain checks passed. New pure checks cover Undo distance restoration with subsequent GPS movement (including after fill-up), invalid numeric rejection, and low-range alert eligibility/latching/threshold boundaries. Runtime Android SharedPreferences snapshot restoration, rotation, notification delivery, and Honda layout have not been tested on a device. Station caches are deliberately retained with visible original-search timestamps rather than presented as current-position results. Tracking Stop summaries persist once per running session, including dashboard editing pauses.

## Version 0.48
Guided Camera2 capture compiles against installed SDK without adding camera dependencies. Existing domain checks pass. Preview/saved-image orientation, guide alignment, autofocus, permission-denial handling, fallback and pause/resume require real-phone testing. Guides are preview-only and never used to discard image pixels before OCR.


## Version 0.56
Confirmed primary-FAB exception from device report. Installed-library runtime check reproduced setFlags(0) retaining the copied primary flag and the resulting FAB rejection; fresh non-primary action passed the same validator. Kotlin source compilation and domain checks passed. Fixed factory creates grid action without primary flag. Live Honda retest remains outstanding.


## Version 0.69 cleanup — October 14, 2026

- Main/debug Kotlin source compilation passed with installed SDK/cached dependencies; existing deprecation warnings remain.
- Executable domain checks passed, including new session restart/stop/clock and last-known-location selection regressions.
- Installed Car App Library Pane primary-action and Grid flag regression checks passed (AutoTemplateCheck.java).
- Removed inactive rendering/helper code and duplicate fuel state; retained saved preference compatibility.
- Corrected Bluetooth monitor/periodic fuel notification ID collision; reduced unchanged station-list redraws and repeated fallback location lookups.
- README replaced with current instructions; historical documentation preserved and labeled.
- No complete Gradle build, signed bundle, ADB installation, device rendering, or battery test was performed in this restricted environment.

## Version 0.70 station map — October 14, 2026

- Added Find gas Map/List switching using host-rendered PlaceListMapTemplate, MAP_TEMPLATES permission, station Place metadata and DistanceSpan rows.
- Current-location display is delegated to Android Auto with permission. Fresh/last-known reference anchors are labeled; default Hell search coordinates cannot become a user-position anchor.
- Local map construction failures return to list. Host rendering errors remain outside this catch.
- Main/debug Kotlin compilation and existing executable regression checks passed. Installed-library map builder constraints were inspected for distance spans, browsable status row, action strip, metadata and loading/list requirements.
- No full Android build, signed bundle or device/host rendering test performed here. Version code 70.

## Version 0.71 Find gas crash correction

Reproduced installed-library failure: ACTIONS_CONSTRAINTS_SIMPLE rejects two actions with custom titles. Version 0.70 added Map alongside Find gas in the ListTemplate action strip. Both station views now use one toolbar text action; search remains a row. Added station construction error reporting/fallback. AutoTemplateCheck covers rejection of two titles and acceptance of one. Main/debug Kotlin source compilation and domain checks passed. No full build or host/device test performed here.

## Version 0.74 adjustable crop areas

Shared normalized, persisted rectangles now drive camera guides, captured-photo adjustment and additional OCR passes. Move/resize bounds remain within the image with a minimum 4% dimension. Settings reject malformed/nonfinite/out-of-bounds rectangles. OCR crops are clamped to bitmap bounds and enlarged with 2048-pixel maximum side; temporary bitmaps are released after sequential recognition. Existing label validation and confirmation remain. Full-frame valid suggestions are retained; crops fill missing fields. Camera capture is blocked during box adjustment. Photo-editor save retries OCR while retaining tracking pause and photo until confirmation/cancellation. Kotlin compile and existing domain regressions passed. Device UI, JPEG/preview alignment and OCR improvement remain unverified.

## Version 0.76 additive photo update

Photo/manual non-fill updates now share FuelEstimateStore.updateMissedDistance; photo trip supplies additive miles and MPG supplies economy. Removed replacement-total mode/neutral switch/reset-at-fillup checkbox from this flow. TOTAL odometer cannot prefill the additive field. Full Kotlin compilation and existing domain checks passed, including additive distance/fuel math. No device UI/full Android build test performed here.

## Version 0.82 configurable disconnect continuation

Added persisted default-on toggle and validated whole-mph threshold (0–100, default 5). Continuation latch takes precedence over subsequent speed/settings changes; Auto connection or recognized Honda session restores normal handling. Threshold is captured in the disconnect notification. Kotlin compile and domain regressions passed for threshold boundaries, disabled behavior and continuation at slow/missing speed. Tests model cleared reconnect state; actual CarConnection/session callback ordering remains unverified without device tests.

## Version 0.87 — October 15, 2026

- Added foreground-only BLE scan screen under phone Advanced, with two-minute timeout, lifecycle stop, scan failure/permission handling, candidate pressure/temperature/age, persistent unique Front/Rear assignments and bounded local copyable raw report.
- Experimental FOBO decoder recognizes manufacturer/service variants; matches protocol fields without assuming authenticated identity or gauge/reference compensation. No GATT writes/connections or background TPMS alarms added.
- Added BLUETOOTH_SCAN/legacy ADMIN and optional BLE feature. Setup checks each requested Bluetooth permission, not just the first.
- Shared rounded phone buttons provide disabled/checked states, ripple, 54dp minimum height and spacing; applied to phone home/settings/Advanced/GPS toggle/TPMS test.
- Manifest XML parsing, all main/debug Kotlin source compilation and existing executable regression checks passed. Added synthetic FOBO pressure, temperature offset, bit-mask, unit and bad-length checks.
- No real FOBO packet fixtures or device UI/scan validation available. No full Android resource/build/link/signing or hardware test claimed. Manual checks: compare both sensors with manufacturer app, verify stale flags, assignment exclusion/persistence, scan stop on background, permission denial, Bluetooth off, and light/dark/disabled button appearance.

## Version 0.92 main-display TPMS integration — October 15, 2026

- Shared assigned-device low-power scan owned by GPS ride service or visible, bounded manual refresh. Filtered manufacturer/service data paths use existing decoder/address checks. Main monitoring ignores unassigned devices.
- Local last-reading persistence with source packet age and no backwards timestamp replacement. Front/rear phone cards and Auto native row/details show PSI/°F and age; stale after >120 seconds; future dates unavailable/stale.
- Foreground service location|connectedDevice declaration and conditional connected-device runtime type; no new background startup path or TPMS alarm system.
- Native Auto Pane retains one existing primary GPS action and uses three rows; tire details uses parked-only refresh and lifecycle-scoped cleanup. No changes to gauge/compass graphic dimensions.
- Kotlin compilation and domain regressions passed (including FOBO fixtures and TpmsAge fresh/stale/future cases); manifest parsed. Preview PNG generated and visually inspected, labeled illustrative/sample data, not a device screenshot.
- Full Android build/resource linking, signed bundle, device rendering and runtime BLE/background/permission/lifecycle behavior remain unverified.

## Version 0.93 tire warning baseline — October 15, 2026

- Per-tire configurable low PSI thresholds (0 disables low); zero pressure takes priority. New fresh-packet transitions post per-tire low/zero notifications, with state deduplication/recovery and reassignment reset.
- Phone reading-card borders yellow/red; Auto native row uses colored Front/Rear image and LOW/ZERO text. Auto tire details uses a dedicated bordered graphic. Host owns native row background styling.
- Zero notification channel offers Android-managed configurable sound; notification volume/DND/permission constraints remain. No forced-volume or DND bypass.
- Optional connected-device foreground service shares the BLE listener while GPS is stopped, with 30-second best-effort checks and explicit stop action. This is listening to sensor advertisements, not active GATT polling or guaranteed 30-second sensor measurements.
- Manifest/service changes, all Kotlin sources and existing executable regressions passed. Pure warning tests cover zero priority, low, normal and disabled-low behavior. No hardware alert/audio/service endurance or actual host-rendering verification performed.

## Version 0.97 Auto dashboard Pane fix

User report identified click listener forbidden by Pane row constraints. Removed the TPMS row listener while retaining menu access to tire details. Actual installed Car App Library RowConstraints regression reproduced the same exception and accepted the nonclickable two-text row. Existing action regressions also passed. Kotlin source compilation/domain checks passed; head rendering still requires device validation.

## Version 0.98 optional map dashboard

Kotlin main/debug source compilation and executable domain checks passed (work/check-098.log). AutoTemplateCheck passed installed-library ROW_LIST_CONSTRAINTS_SIMPLE for browsable two-line detail rows and ACTIONS_CONSTRAINTS_NAVIGATION for two icon actions plus one text action; existing Pane/Grid regressions pass. Full PlaceListMapTemplate setItemList cannot run against android.jar JVM stubs because ModelUtils constructs SpannableString; actual Android runtime validation is outstanding. No full Gradle/signed bundle or DHU/Honda rendering verified. Test map layout/camera, permission denial/no fix, fresh GPS anchor, saved POI, play/pause behavior, mode switching and fallback on-device.

## Version 0.99 dashboard switch

Installed-library validators accepted Gauges SIMPLE strip (mode icon + Find gas), Map NAVIGATION strip (three icons + Find gas), and Pane primary GPS + Settings actions. Kotlin source compilation and domain checks passed (work/check-099.log). Actual icon appearance/order and mode switching on Honda/DHU require device validation. No signed bundle generated.

## Version 0.100 Find gas entry request

Kotlin compilation and existing executable domain checks passed (work/check-100.log). Reviewed request ownership release on stop, permission failures and asynchronous completion; cross-controller search guard; cooldown-deferred entry search and cancellation on stop; one entry request per station screen; main Find gas call sites opt in while saved-POI browsing does not. Gauges Map view is the second unflagged Pane action alongside primary GPS, retaining the previously validated Pane/header action shapes. Lifecycle/network behavior and final layout need on-device testing.

## Version 0.101 station reliability

All main/debug Kotlin sources compiled and executable domain regressions passed (work/check-101.log). Added retry boundary tests for timeout/connect/reset, no retry after five retries, no retry for TLS/DNS, increasing delays and longer Retry-After. Existing HTTP classification and partial-result checks pass. Reviewed saved-result retention and six-attempt diagnostic history. Public service availability/network behavior not live-tested; exact user failure remains unconfirmed without the on-device report.

## Version 0.102 station map list

Kotlin compilation/domain regressions passed. Reviewed main dashboard now iterates every stored station, with current-position sorting, per-station distance spans and Place metadata. Search map/list titles include returned count. Actual host viewport/scrolling and marker framing require Honda/DHU validation.

## Version 0.103 search completeness

Kotlin compilation/domain regressions passed. Tested expansion for result counts 0,1,2 through 5/10/25 km, no expansion at 3 or radius cap; geographic fixtures verify 1→2→3 results. Reviewed main-thread publication of intermediate results guarded by lifecycle generation and location permission; wider-request failures preserve published results. Live Overpass/Honda testing outstanding.

## Version 0.104 map visibility

Reviewed station-first ordering in both maps, single-line rows with real DistanceSpan in title and numbered Place metadata, fresh-only anchor and permission-controlled host location dot. Kotlin source compilation/domain checks passed (work/check-104.log). Cannot verify simultaneous visibility without host runtime; documented host-visible-row restriction and lack of fit-bounds control, rather than claiming a guaranteed camera fit.

## Version 0.105 ride/search map separation

Kotlin compilation/domain checks passed. MapDashboard no longer reads StationResultsStore or creates station Place metadata; its two browsable summary rows retain existing validated row/action structures. StationSearchScreen and StationMapRows unchanged. Host rendering remains to be verified on device.

## Version 0.106 miles search radii

Compilation/domain checks passed. Radius policy tests cover 15→30→60 mi with 0/1/2 results, stop at 3 and maximum; geographic fixtures verify result growth at converted radii. Formatting tests verify 15 mi, 30 mi and 60 mi. No km text remains in FuelSearchController user/report messages; wire radius remains meters. Live service load/host display unverified.

## Version 0.107 unit preferences

All main/debug Kotlin sources and executable domain checks passed (work/check-107.log). Tests cover miles/km, mph/km/h, US/Imperial gallons/liters, four economy formats, PSI/kPa/bar, C/F/null, conversion round trips and known equivalences; fuel-estimation invariance after metric entry; current/legacy session formatting; metric and Imperial photo normalization, zero economy rejection, ambiguous/trip/speed rejection, thousands separators. Existing regressions continue passing. Reviewed preference defaults, conversion at entry/save boundaries, exact unedited threshold retention, structured completed-session values, station DistanceSpan units and notification formatting. Physical search radii, raw recorded diagnostics, degrees/time conventions and existing canonical storage remain explicit. Android dialog, runtime migration and actual host formatting still require device testing; no signed AAB generated.

## Version 0.108 idle tire windows

Main/debug Kotlin compilation and executable regressions passed. Added pure TPMS poll-window tests for both fresh IDs, repeated front, unrelated IDs, pre-window cached measurement, receipt beyond the 15-second deadline, single assigned sensor and empty set. Stale cutoff tests verify missing/older readings stale, newer reading recovery and no false stale without a timeout. Reviewed monitor owner isolation/cancellation, scan-failure timeout retention, permission/Bluetooth errors, persisted stale labels, manual refresh routing and stale-zero repeat suppression. Physical advertising intervals and Android scheduling/runtime behavior remain unverified.

## Version 0.109 continuous listening

Main/debug Kotlin compilation and executable regressions passed. Added continuous stale-policy tests for just-before/exact deadline, absent initial reading, negative age and fresh recovery. Reviewed background owner remains continuous across five-second watchdog ticks, scan failure recovery, owner-specific stops, initial grace period, persisted staleness and fresh packet/zero-repeat behavior. Runtime BLE battery usage, scheduling and service restart behavior remain unverified.

## Version 0.110 default background monitoring

Kotlin main/debug source compilation passed. Reviewed shared default-true accessor at all three preference reads and unchanged explicit false writes from Settings/Stop monitoring. No new behavior-mirroring tests added for this preference-only change. Android startup and BLE hardware timing remain unverified.

## Version 0.111 data and monitoring additions

All main/debug Kotlin source compilation and executable domain regressions passed (work/check-111.log). New checks cover all descriptive confidence paths, interval averaging/duplicate exclusion, CSV quoting/formula prevention, backup allowed keys/types/ranges/units/MAC/crop validation, rejection of active/readings fields, and unverified battery-evidence variants. Updated FOBO value tests retain original pressure/temp assertions independently of new evidence fields. Fixed Unicode corruption discovered by unit-label regression checks. Installed-library AutoTemplateCheck passed existing two-text Pane/row and header/action constraints; manifest XML parsed with new activity/receivers and boot permission.

Reviewed system PendingIntent explicit target/mutability, disabled-state guards, Bluetooth/reboot/package recovery hooks, scan backoff, no background FGS launch from receivers, permission/assignment prerequisites, START_STICKY rejection paths and sensor timestamp age gates. Reviewed backup parse-before-write, restored stopped-only state, rollback attempt, no live-reading resurrection, historical unit formats, Undo marking and CSV canonical unit headers. No Android runtime file picker/JSON restore, recovery/process-death, physical battery drain, sensor battery field confirmation or actual host render verification available. Do not claim guaranteed monitoring uptime, negligible power, verified FOBO battery percent, historical reconstruction or signed-bundle completion.

## Version 0.112 freshness and position status

Kotlin compilation and executable regressions passed (work/check-112.log). Tests cover tire freshness just before/exactly five minutes, age-label consistency, missing initial readings and zero-warning cessation. GPS display tests cover fresh/20s/30s, invalid/future/over-age timestamps, NaN and 100/101 m accuracy; distance accumulation quality tests unchanged and passing. Reviewed host callback now feeds display independent of ride service, raw/source identity warnings retained and fuel confidence removed from map GPS status. Device callback cadence and actual user GPS message require on-device verification.

## Version 0.113 bounded gap interpolation

Kotlin main/debug compilation and executable regressions passed (work/check-113.log). Added recovered >30s gap estimate and subsequent measured step, stationary outage, explicit disable/restart reset, 99/101 mph plausibility boundary, spike anchor discard, source-change behavior and estimate-confidence branch. Existing coordinate/time/accuracy/drift and fuel regressions pass. Reviewed GPS disabled gate, startup reset, separate total/session estimates, full-fill/total correction reset, Undo retention and JSON/CSV/backup field validation. Route distance during an outage is unknowable from endpoints; no route/speed extrapolation or fake map position is claimed. Android runtime storage, undo and physical outage tests remain outstanding.

## 0.114
Fuel confidence wording now names missing/interrupted distance explicitly. Phone and Auto show a four-segment GPS fix-quality bar based on age and reported accuracy (not satellite signal strength). Removed primary-action flag from GPS control and limited dashboard rebuilds to 15-second intervals to reduce rotary focus resets. Verify on Honda: move focus between GPS, Map view, Settings and Find gas while tracking; host may still reset focus on periodic refresh. No host focus-restoration API is used.


## 0.115
Experimental Bike 2 battery percentage: unsigned byte6 multiplied by two, per user hypothesis. Only 0–100 accepted; no clamping or battery alarms. Persisted with packet and shown on phone tire cards and Auto tire details, explicitly experimental/unverified; stale readings labeled. Existing saved readings need a new packet before a percentage is available. Test samples cover 86%, 90%, 96%, endpoints and invalid values. Not a verified protocol or battery-health scale.

