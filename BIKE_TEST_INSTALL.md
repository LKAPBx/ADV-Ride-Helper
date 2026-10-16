> Historical guide: some controls and version-specific steps below have changed. See [the current README](README.md) for version 0.69 behavior and installation.

# Private bike test through Google Play

**Current version 8 (0.8):** Sync Studio for the new OCR library, generate a signed release AAB with your existing key, upload code 8 and roll out to Internal testing, then update through Play. This version removes all bike telemetry controls and adds photo-confirmed odometer/average-MPG fuel estimates. See [current workflow](PHOTO_FUEL_SETUP.md). Earlier instructions for telemetry permissions are obsolete.

**Current version 5 (0.5):** Generate a signed release AAB for Internal testing and update through Play. Adds GPS ride tracking and an estimated-fuel screen. See [GPS setup and initial validation](GPS_RIDE_SETUP.md). Complete any new foreground-location-service declarations Play Console requests. Earlier version notes below are historical.

**Current version 4 (0.4):** Adds make/model/year, fuel-type codes, raw/display speed and odometer diagnostics. Generate a new signed release or bikeTest AAB with the existing key, roll code 4 out to Internal testing, and update through Play. On the phone tap **Allow speed and odometer probes** and grant them if offered. Fuel types use the existing fuel permission; identity requires no extra projected permission. On the motorcycle tap the **Fuel level (test)** row to open **Vehicle diagnostics**. Reopen the car app after changing permissions.

Each field shows SUCCESS with a usable value, UNKNOWN, UNAVAILABLE, UNIMPLEMENTED, permission/access denial, no callback or stale measurement. Speed is host-reported raw/display speed converted from m/s to mph; odometer is host-reported meters converted to miles. No phone-GPS speed or calculated trip distance is substituted. Fuel types display reported numeric codes, without guessing their meaning. Identity/fuel profile are static queries; speed/mileage listeners are removed on stop, and dynamic readings older than 60 seconds are not shown as current. All values/statuses/timestamps also log under ATTelemetry. DHU data can be simulated and does not establish Honda support.

**Current version 3 (0.3):** Fuel probing, fuel display and CAR_FUEL permission are now included in **both release and bikeTest**, so selecting release no longer removes the feature. Generate a signed release or bikeTest AAB with your existing upload key, roll version 3 out to Internal testing, and update through Play. The phone must show Allow fuel-data probe. Earlier variant-specific instructions below describe versions 1–2 and are superseded by this note. This remains a private diagnostics prototype; public category eligibility is not established by enabling the probe.

**Version 2 update:** Fixes the real-bike `Unrecognized host` rejection by explicitly allowing Android Auto's observed package/certificate, verified against the official library sample allowlist. Generate a new signed **bikeTest** AAB with your existing upload keystore, upload it as a new Internal testing release, roll it out privately, and update from Play. Confirm version 0.2 (code 2). Do not use Studio Run to replace the Play installation.

**Required Play configuration:** In the app's Play Console, open Advanced settings > Form factors > Add form factor > Android Auto, and complete the requirements shown there. Choose Android Auto, not Android Automotive OS. Google requires this opt-in in addition to a testing-track release. Internal testing has no automotive form-factor review. See [distribution requirements](https://developer.android.com/training/cars/distribute). If the Play-installed app is missing from Customize launcher, verify this setting before rebuilding.

This project now has a `bikeTest` build type. It inherits release settings, remains non-debuggable, retains host validation, includes the fuel probe and CAR_FUEL permission, and omits the fictional debug station. The standard release still omits fuel probing. Use bikeTest for the private Internal testing track, not the ordinary release or debug build.

1. Sync the changed project in Android Studio.
2. Build > Generate Signed Bundle/APK > Android App Bundle. Choose module app.
3. Create an upload keystore through Create new if needed, choose an alias and strong passwords, and validity of at least 25 years. Keep the keystore and passwords privately backed up. They are not needed in chat or source control.
4. Continue and select build variant **bikeTest**, then generate the signed bundle. No signed bundle has been created by Codex; the wizard requires your upload key.
5. In Play Console select AfricaTwinTelemetry > Test and release > Testing > Internal testing. Add your phone's Google account as a tester. Create a release and upload the generated `.aab`. Configure Play App Signing and complete any Console requirements before rolling out privately.
6. Open the tester opt-in link on the phone, join the test, and install through Play. If Play reports a signing conflict with the Android Studio installation, uninstall that local app first (this removes saved app settings), then install from Play.
7. Enable station search/location and Allow fuel-data probe on the phone. Connect to the motorcycle's Android Auto while parked and open the app.

The fuel row may show percentage, permission denial, unavailable/unimplemented data, no response or stale data. No Honda sensor support is asserted. The DHU remains a simulated head unit. Play can impose additional account, release or automotive requirements; report the exact message if blocked. An internal rollout is private and does not require a public production release.

Validation: existing debug/release source variants checked; new Gradle build-type wiring and signed bundle require Studio sync/build. Do not reuse older APKs.

Sources: [App signing](https://developer.android.com/studio/publish/app-signing), [Internal testing](https://support.google.com/googleplay/android-developer/answer/9845334), [Android Auto real-vehicle testing](https://developer.android.com/training/cars/testing).

