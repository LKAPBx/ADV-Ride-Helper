package dev.africatwin.telemetry

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.*

internal fun MainActivity.advanced() {
        val activity = this
        // TPMS scanning stays in a foreground diagnostic screen until readings are verified.
        val tpmsIntent = Intent(this, TpmsScanActivity::class.java)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(12)) }
        if (DeveloperOptions.AVAILABLE) {
            box.addView(Switch(this).apply {
                text = "Disable phone ads on this device"
                isChecked = DeveloperOptions.adsDisabled(activity)
                setOnCheckedChangeListener { _, disabled ->
                    DeveloperOptions.setAdsDisabled(activity, disabled)
                    adSpace.visibility = if (disabled) View.GONE else View.VISIBLE
                }
            })
            box.addView(label("Local developer override. Does not grant or restore a paid purchase.", 13f))
        }
        box.addView(button("Scan TPMS sensors (FOBO Bike 2 test)") { startActivity(tpmsIntent) })
        box.addView(button("Inspect head-unit Bluetooth services") { startActivity(Intent(this, BluetoothInspectActivity::class.java)) })
        box.addView(button("OCR crop coordinates") { DashboardCropAreas.showReport(this) })
        box.addView(button("Copy saved app error") {
            getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("App error", CrashReports.report(this)))
            message("Error report copied")
        })
        box.addView(button("GPS start report") {
            val report = TrackingStartReport.read(this)
            AlertDialog.Builder(this).setTitle("GPS start report").setMessage(report)
                .setPositiveButton("Close", null).setNeutralButton("Copy report") { _, _ ->
                    getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("GPS start report", report))
                    message("GPS start report copied")
                }.show()
        })
        box.addView(button("Test fuel & range notification") {
            message(if (FuelSummaryNotice.show(this)) "Notification sent; check Android Auto and phone" else "Notification unavailable; check notification permission")
        })
        box.addView(button("Saved head-unit info") { headUnitInfo() })
        box.addView(button("Station search report") {
            val report = getSharedPreferences("station_diagnostics", Context.MODE_PRIVATE).getString("report", "No station request recorded yet.") ?: "No report"
            AlertDialog.Builder(this).setTitle("Latest station search")
                .setMessage(report).setPositiveButton("Close", null)
                .setNeutralButton("Copy report") { _, _ ->
                    getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Station search report", report))
                    message("Report copied")
                }.show()
        })
        AlertDialog.Builder(this).setTitle("Developer & advanced").setView(ScrollView(this).apply { addView(box) }).setPositiveButton("Close", null).show()
    }
internal fun MainActivity.options() {
        val activity = this
        optionStates.clear()
        val units = AppUnits.get(this)
        var settingsDialog: AlertDialog? = null
        val estimate = FuelEstimateStore(this)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        box.addView(button("Units") { AppUnits.settings(this) { settingsDialog?.dismiss(); updateSummary(); options() } })
        box.addView(label("Tank size (${units.volumeUnit})", 20f, true))
        val capacity = numeric("Tank capacity (${units.volumeUnit})", units.volumeValue(estimate.capacity)); box.addView(capacity)
        box.addView(label("Tank size applies to your next full fill-up.", 13f))
        box.addView(label("Tire pressure", 20f, true))
        box.addView(Switch(this).apply {
            text = "Enable TPMS on phone and head unit"
            isChecked = TpmsFeature.enabled(activity)
            setOnCheckedChangeListener { _, enabled -> TpmsFeature.setEnabled(activity, enabled); updateSummary() }
        })
        box.addView(label("Turn off to hide tire data on both displays and stop tire scanning and pressure warnings. Sensor assignments and warning settings are kept.", 13f))
        box.addView(button("Tire pressure warnings & monitoring") { TpmsAlerts.settings(this) })
        box.addView(button("Assign tire sensors") { startActivity(Intent(this, TpmsScanActivity::class.java)) })
        box.addView(label("Gas stations", 20f, true))
        val autoStations = button("Auto-find gas at ${FuelSearchController.autoThreshold(this)}% fuel") { }
        autoStations.setOnClickListener {
            val percent = numeric("Fuel percentage (1–100)", FuelSearchController.autoThreshold(this).toDouble())
            val dialog = AlertDialog.Builder(this).setTitle("Automatic station search")
                .setMessage("Search for gas when estimated fuel reaches this level or lower. Runs while the phone app is open, at most once every 5 minutes. Default: 10%. You can use Find gas at any fuel level.")
                .setView(percent).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val value = percent.text.toString().toIntOrNull()
                    if (value == null || value !in 1..100) percent.error = "Enter a whole percentage from 1 to 100"
                    else {
                        getSharedPreferences("station_search_options", Context.MODE_PRIVATE).edit().putInt("autoPercent", value).apply()
                        autoStations.text = "Auto-find gas at $value% fuel"; dialog.dismiss()
                    }
                }
            }
            dialog.show()
        }
        box.addView(autoStations)
        box.addView(label("Station searches send your location to Overpass. Map data: © OpenStreetMap contributors.", 13f))
        box.addView(label("Notifications", 20f, true))
        val noticePrefs = getSharedPreferences("fuel_notice", Context.MODE_PRIVATE)
        box.addView(Switch(this).apply {
            text = "Low-range alert"
            isChecked = noticePrefs.getBoolean("lowEnabled", true)
            setOnCheckedChangeListener { _, enabled -> noticePrefs.edit().putBoolean("lowEnabled", enabled).apply() }
        })
        val lowRange = button("Alert at: ${units.distance(noticePrefs.getInt("lowMiles", 40).toDouble(), 0)} remaining") { }
        lowRange.setOnClickListener {
            val thresholds = intArrayOf(20, 40, 60, 80)
            AlertDialog.Builder(this).setTitle("Low-range alert threshold")
                .setItems(thresholds.map { units.distance(it.toDouble(), 0) }.toTypedArray()) { _, index ->
                    noticePrefs.edit().putInt("lowMiles", thresholds[index]).apply()
                    lowRange.text = "Alert at: ${units.distance(thresholds[index].toDouble(), 0)} remaining"
                }.show()
        }
        box.addView(lowRange)
        box.addView(label("Alerts the user when the estimated range reaches the threshold during tracking. Resets after Fill up. Default: ${units.distance(40.0, 0)}.", 13f))
        box.addView(Switch(this).apply {
            text = "Notify when a ride ends"
            isChecked = noticePrefs.getBoolean("stopSummary", true)
            setOnCheckedChangeListener { _, enabled -> noticePrefs.edit().putBoolean("stopSummary", enabled).apply() }
        })
        box.addView(Switch(this).apply {
            text = "Fuel and range reminders"
            isChecked = FuelSummaryNotice.enabled(activity)
            setOnCheckedChangeListener { _, enabled ->
                getSharedPreferences("fuel_notice", Context.MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
                if (!enabled) FuelSummaryNotice.cancel(activity)
            }
        })
        val noticeInterval = button("Remind every: ${FuelSummaryNotice.interval(this)} minutes") { }
        noticeInterval.setOnClickListener {
            val intervals = intArrayOf(5, 10, 15, 30)
            AlertDialog.Builder(this).setTitle("Fuel and range reminder interval")
                .setItems(intervals.map { "$it minutes" }.toTypedArray()) { _, selected ->
                    getSharedPreferences("fuel_notice", Context.MODE_PRIVATE).edit().putInt("minutes", intervals[selected]).apply()
                    noticeInterval.text = "Remind every: ${intervals[selected]} minutes"
                }.show()
        }
        box.addView(noticeInterval)
        box.addView(label("Reminders appear during tracking. Default: every 10 minutes.", 13f))
        box.addView(button("Full version • remove ads") { PhoneAdSpace.showInfo(this) })
        box.addView(label("Appearance", 20f, true))
        box.addView(label("Head-unit background picture applies to the gauge panel. Android Auto controls the rest of the screen and map. Pictures are cropped to a square and darkened for readability.", 13f))
        box.addView(button("Choose head-unit background picture") {
            try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 21) }
            catch (_: RuntimeException) { message("No image picker available.") }
        })
        box.addView(button("Remove head-unit background picture") {
            HeadBackground.clear(this); message("Default head-unit gauge background restored.")
        })
        val dashboardMode = button("Head-unit dashboard: ${if (MapDashboard.enabled(this)) "Map" else "Gauges"}") { }
        dashboardMode.setOnClickListener {
            AlertDialog.Builder(this).setTitle("Head-unit dashboard").setSingleChoiceItems(arrayOf("Gauges", "Map"), if (MapDashboard.enabled(this)) 1 else 0) { dialog, selected ->
                MapDashboard.setEnabled(this, selected == 1)
                dashboardMode.text = "Head-unit dashboard: ${if (selected == 1) "Map" else "Gauges"}"
                dialog.dismiss()
            }.show()
        }
        box.addView(dashboardMode)
        box.addView(button("Appearance: System / Light / Dark") {
            AlertDialog.Builder(this).setTitle("Appearance").setItems(arrayOf("Follow phone setting", "Light", "Dark")) { _, selected ->
                getSharedPreferences("appearance", Context.MODE_PRIVATE).edit().putString("mode", arrayOf("system", "light", "dark")[selected]).apply()
                recreate()
            }.show()
        })
        box.addView(label("Automatic Session Start/Stop", 20f, true))
        box.addView(label("The app tries to start tracking when your head-unit connects to Android Auto. Keep connection monitoring on and allow location all the time.", 13f))
        val disconnectSpeed = button("Continue above: ${units.speed(RideDisconnect.thresholdMph(this).toDouble())}") { }
        disconnectSpeed.isEnabled = RideDisconnect.enabled(this)
        box.addView(Switch(this).apply {
            text = "Continue tracking after head-unit or Bluetooth disconnect"
            isChecked = RideDisconnect.enabled(activity)
            setOnCheckedChangeListener { _, enabled ->
                RideDisconnect.setEnabled(activity, enabled)
                disconnectSpeed.isEnabled = enabled
            }
        })
        disconnectSpeed.setOnClickListener {
            val value = numeric("Speed threshold (${units.speedUnit})", units.speedValue(RideDisconnect.thresholdMph(this).toDouble()))
            val dialog = AlertDialog.Builder(this).setTitle("Continue tracking speed")
                .setMessage("Continue after a disconnect only when fresh GPS speed is above this value. Default: ${units.speed(5.0)}.")
                .setView(value).setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val speed = value.text.toString().toDoubleOrNull()?.let(units::speedInput)?.takeIf { it.isFinite() && it in 0.0..100.0 }?.let { kotlin.math.round(it).toInt() }
                    if (speed == null || speed !in 0..100) value.error = "Enter a speed from 0 to ${units.speed(100.0)}"
                    else {
                        RideDisconnect.setThresholdMph(this, speed)
                        disconnectSpeed.text = "Continue above: ${units.speed(speed.toDouble())}"; dialog.dismiss()
                    }
                }
            }
            dialog.show()
        }
        box.addView(disconnectSpeed)
        box.addView(label("If the connection drops while you’re above the selected speed, tracking continues and a notification appears. Use Stop GPS to end the session, even if you slow down, lose GPS, or turn this option off. Reconnecting Android Auto restores normal start/stop behavior.", 13f))
        val deviceMatch = button("Head-unit Bluetooth name: ${HondaAutoTracking.deviceName(this)}") { }
        deviceMatch.setOnClickListener {
            val name = EditText(this).apply { setSingleLine(true); setText(HondaAutoTracking.deviceName(activity)) }
            val dialog = AlertDialog.Builder(this).setTitle("Bluetooth device name")
                .setMessage("Match any device whose name contains this text, ignoring capitalization. Default: Honda Motorcycle. Android Auto must also be connected.")
                .setView(name).setNegativeButton("Cancel", null).setNeutralButton("Default", null).setPositiveButton("Save", null).create()
            dialog.setOnShowListener {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { name.setText("Honda Motorcycle") }
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    val value = name.text.toString().trim()
                    if (value.isEmpty() || value.length > 100) name.error = "Enter a device name from 1 to 100 characters"
                    else {
                        HondaAutoTracking.setDeviceName(this, value)
                        deviceMatch.text = "Head-unit Bluetooth name: $value"
                        if (bluetoothAllowed() && HondaAutoTracking.bluetoothEnabled(this)) {
                            try { startForegroundService(Intent(this, BikeConnectionService::class.java).putExtra("refreshDeviceMatch", true)) }
                            catch (_: RuntimeException) { message("Name saved. Reconnect the device to restart monitoring.") }
                        }
                        dialog.dismiss()
                    }
                }
            }
            dialog.show()
        }
        box.addView(deviceMatch)
        box.addView(permissionToggle("Monitor Bluetooth connection", { HondaAutoTracking.bluetoothEnabled(this) && bluetoothAllowed() }) { enabled ->
            HondaAutoTracking.setBluetoothEnabled(this, enabled)
            if (!enabled) stopService(Intent(this, BikeConnectionService::class.java))
            else if (!bluetoothAllowed()) requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 7)
            else try { startForegroundService(Intent(this, BikeConnectionService::class.java)) }
            catch (_: RuntimeException) { message("Could not start connection monitoring.") }
        })
        box.addView(label("Tracking starts when a Bluetooth device whose name contains the saved text is connected and Android Auto is active. Matching ignores capitalization. A notification shows that connection monitoring is running. After changing the name or granting Bluetooth access, reconnect the device if needed. You can also start tracking from the main screen.", 13f))
        box.addView(label("Permissions", 20f, true))
        box.addView(button("Set up permissions") { firstRunPermissions?.retry() })
        box.addView(permissionToggle("Allow location all the time", { preciseAllowed() && (Build.VERSION.SDK_INT < 29 || checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED) }) { enabled ->
            if (!enabled) appSettings() else backgroundLocationSetup()
        })
        box.addView(label("Needed to start rides while the phone app is closed. Choose Permissions → Location → Allow all the time and leave precise location on.", 13f))
        box.addView(permissionToggle("Precise location", { preciseAllowed() }) { enabled ->
            if (enabled) requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 2)
            else appSettings()
        })
        box.addView(permissionToggle("Notifications", { notificationsAllowed() }) { enabled ->
            if (enabled && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 4)
            else startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
        })
        box.addView(label("More", 20f, true))
        box.addView(button("About ADV Ride Helper") { about() })
        box.addView(button("History, backup & monitoring") { startActivity(Intent(this, RideDataActivity::class.java)) })
        val error = label("", 13f); box.addView(error)
        val dialog = AlertDialog.Builder(this).setTitle("Settings").setView(ScrollView(this).apply { addView(box) })
            .setNegativeButton("Close", null).setPositiveButton("Save", null).create()
        settingsDialog = dialog
        dialog.setOnDismissListener { optionStates.clear() }
        dialog.setOnShowListener { dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val value = capacity.text.toString().toDoubleOrNull()?.let(units::volumeInput)
            if (value == null || !value.isFinite() || value !in 0.1..20.0) error.text = "Enter a capacity from ${units.volume(0.1)} to ${units.volume(20.0)}."
            else { estimate.setCapacity(value); updateSummary(); dialog.dismiss() }
        } }
        dialog.show()
    }
