package dev.africatwin.telemetry

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.ClipData
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.*
import android.text.InputType
import android.widget.*
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import java.io.File

class MainActivity : Activity() {
    private lateinit var stationResults: LinearLayout
    private var stationRenderKey: String? = null
    private lateinit var stationSearch: FuelSearchController
    private lateinit var findGasButton: Button
    private var dark = false
    private var recoveryMode = false
    private var firstRunPermissions: FirstRunPermissions? = null
    private var editingRide = false
    private var resumeRide = false
    private var editStopSerial = 0L
    private var editFlow = "reading"
    private var externalReading = false
    private fun pauseForEdit() {
        if (editingRide) return
        editingRide = true; resumeRide = RideStore(this).running
        if (resumeRide) RideControls.stop(this, "GPS paused while editing fuel/distance")
        val prefs = getSharedPreferences("ride_edit", MODE_PRIVATE)
        editStopSerial = prefs.getLong("stopSerial", 0)
        prefs.edit().putBoolean("editing", true).apply()
        updateSummary()
    }
    private fun finishEdit() {
        if (!editingRide) return
        val prefs = getSharedPreferences("ride_edit", MODE_PRIVATE)
        val restart = resumeRide && prefs.getLong("stopSerial", 0) == editStopSerial
        editingRide = false; resumeRide = false
        prefs.edit().putBoolean("editing", false).apply()
        if (restart) startTracking()
    }
    private val optionStates = mutableListOf<Pair<Switch, () -> Boolean>>()
    private fun refreshOptionStates() { optionStates.forEach { (control, state) -> control.isChecked = state() } }
    private fun permissionToggle(title: String, state: () -> Boolean, action: (Boolean) -> Unit): Switch = Switch(this).apply {
        text = title; isChecked = state(); optionStates.add(this to state)
        setOnClickListener { action(isChecked); refreshOptionStates() }
    }
    private fun appSettings() { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
    private fun preciseAllowed() = checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun backgroundLocationSetup() {
        if (!preciseAllowed()) {
            AlertDialog.Builder(this).setTitle("Location setup: step 1 of 2")
                .setMessage("First choose Precise and While using the app. Then we will show the separate Allow all the time setup for automatic Honda rides. ADV Ride Helper uses location to record ride distance even when the app is closed or not in use.")
                .setNegativeButton("Cancel", null).setPositiveButton("Continue") { _, _ ->
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 8)
                }.show()
            return
        }
        if (Build.VERSION.SDK_INT < 29) { message("This Android version needs no separate background permission."); return }
        val option = if (Build.VERSION.SDK_INT >= 30) packageManager.backgroundPermissionOptionLabel.toString() else "Allow all the time"
        AlertDialog.Builder(this).setTitle("Location setup: allow all the time")
            .setMessage("ADV Ride Helper uses location to record ride distance and start Honda rides even when the app is closed or not in use. Choose $option and keep precise location enabled. On newer Android versions this is a separate settings step; While using the app does not enable automatic background starts. You can decline and use manual Start.")
            .setNegativeButton("Cancel", null).setNeutralButton("App settings") { _, _ -> appSettings() }
            .setPositiveButton("Continue") { _, _ ->
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 6)
            }.show()
    }
    private fun bluetoothAllowed() = Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    private fun notificationsAllowed(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        return (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) && manager.areNotificationsEnabled() &&
            listOf("ride", "tracking_changes").none { manager.getNotificationChannel(it)?.importance == NotificationManager.IMPORTANCE_NONE }
    }
    private val pageColor get() = if (dark) Color.rgb(18, 24, 22) else Color.rgb(245, 248, 245)
    private val textColor get() = if (dark) Color.rgb(226, 237, 230) else Color.rgb(28, 49, 39)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var fuel: TextView
    private lateinit var range: TextView
    private lateinit var details: TextView
    private lateinit var quality: TextView
    private lateinit var gauge: FuelGaugeView
    private lateinit var rangeGauge: ProgressBar
    private lateinit var fuelScale: TextView
    private lateinit var rangeScale: TextView
    private lateinit var savedReading: TextView
    private lateinit var tracking: ToggleButton
    private lateinit var trackingStatus: TextView
    private lateinit var undoReading: Button
    private lateinit var sessionTotals: TextView
    private lateinit var frontTire: TextView
    private lateinit var rearTire: TextView
    private lateinit var tireHealth: TextView
    private var startAfterPermission = false
    private var pendingFill = false
    private var cameraPath: String? = null
    private var pendingPhoto: Uri? = null
    private var scanning = false
    private lateinit var photoStatus: TextView
    private val refresh: Runnable = object : Runnable { override fun run() { updateSummary(); handler.postDelayed(this, 2000) } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun label(text: String, size: Float = 16f, bold: Boolean = false) = TextView(this).apply {
        this.text = text; textSize = size; setTextColor(textColor); setPadding(0, dp(6), 0, dp(6))
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; PhoneButtons.style(this, dark); setOnClickListener { action() }
    }
    private fun bar(color: Int) = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 1000; progressTintList = android.content.res.ColorStateList.valueOf(color)
        progressBackgroundTintList = android.content.res.ColorStateList.valueOf(if (dark) Color.rgb(60, 72, 66) else Color.rgb(225, 231, 228))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(22))
    }
    override fun onCreate(state: Bundle?) {
        if (CrashReports.pending(this)) {
            recoveryMode = true
            setTheme(android.R.style.Theme_Material_Light_NoActionBar)
            super.onCreate(state)
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(40), dp(20), dp(40)) }
            box.addView(label("Saved app error", 24f, true))
            box.addView(label("Copy this report so we can identify the crash. Your saved fuel and ride data have not been cleared.", 16f))
            box.addView(button("Copy error report") {
                getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("App error", CrashReports.report(this)))
                message("Error report copied")
            })
            box.addView(button("Continue to app") { CrashReports.acknowledge(this); recreate() })
            box.addView(label(CrashReports.report(this), 12f).apply { setTextIsSelectable(true) })
            setContentView(ScrollView(this).apply { addView(box) })
            return
        }
        val appearance = getSharedPreferences("appearance", MODE_PRIVATE).getString("mode", "system")
        dark = appearance == "dark" || (appearance == "system" && resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        firstRunPermissions = FirstRunPermissions(this, state) {
            refreshOptionStates()
            TpmsRecovery.startFromUi(this)
            if (bluetoothAllowed() && HondaAutoTracking.bluetoothEnabled(this)) {
                try { startForegroundService(Intent(this, BikeConnectionService::class.java)) }
                catch (_: RuntimeException) { message("Reconnect the bike to retry Bluetooth monitoring.") }
            }
            if (intent.action == RideControls.START_ON_PHONE) {
                intent.action = null
                handler.post { if (!isFinishing && !isDestroyed) startTracking() }
            }
        }
        editingRide = state?.getBoolean("editingRide") ?: false
        resumeRide = state?.getBoolean("resumeRide") ?: false
        editStopSerial = state?.getLong("editStopSerial") ?: 0L
        editFlow = state?.getString("editFlow") ?: "reading"
        externalReading = state?.getBoolean("externalReading") ?: false
        getSharedPreferences("ride_edit", MODE_PRIVATE).edit().putBoolean("editing", editingRide).apply()
        pendingFill = state?.getBoolean("pendingFill") ?: false
        cameraPath = state?.getString("cameraPath")
        pendingPhoto = state?.getString("pendingPhoto")?.let(Uri::parse)
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(20), dp(20), dp(28)); setBackgroundColor(pageColor) }
        layout.addView(label("ADV Ride Helper", 28f, true))
        layout.addView(label("Fuel & range estimate", 17f))
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable().apply { setColor(if (dark) Color.rgb(30, 40, 35) else Color.WHITE); cornerRadius = dp(18).toFloat() }
        }
        card.addView(label("ESTIMATED FUEL", 12f, true)); fuel = label("—", 36f, true); card.addView(fuel)
        gauge = FuelGaugeView(this, dark).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(170))
        }; card.addView(gauge)
        fuelScale = label("", 12f); card.addView(fuelScale)
        card.addView(label("Estimated range to empty", 14f)); range = label("—", 26f, true); card.addView(range)
        rangeGauge = bar(Color.rgb(37, 99, 180)); card.addView(rangeGauge)
        rangeScale = label("", 12f); card.addView(rangeScale)
        details = label(""); quality = label("", 13f); card.addView(details); card.addView(quality); layout.addView(card)
        savedReading = label("", 13f); layout.addView(savedReading)
        layout.addView(label("Tire pressure", 20f, true))
        tireHealth = label("", 13f); layout.addView(tireHealth)
        val tires = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun tireCard(title: String): TextView {
            val content = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(16).toFloat(); setColor(if (dark) Color.rgb(29, 48, 40) else Color.rgb(225, 239, 231))
                }
            }
            content.addView(label(title, 16f, true))
            val reading = label("Waiting for sensor", 18f, true); content.addView(reading)
            tires.addView(content, LinearLayout.LayoutParams(0, -1, 1f).apply { setMargins(dp(3), dp(4), dp(3), dp(4)) })
            return reading
        }
        frontTire = tireCard("Front"); rearTire = tireCard("Rear"); layout.addView(tires)
        layout.addView(button("Refresh tire readings") {
            if (!TpmsMonitor.allowed(this)) requestPermissions(TpmsMonitor.permissions(), 9)
            else message(TpmsMonitor.refresh(this, "phone"))
        })
        tracking = ToggleButton(this).apply {
            PhoneButtons.style(this, dark)
            textOff = "GPS tracking OFF — tap to start"; textOn = "GPS tracking ON — tap to stop"
            isAllCaps = false; setOnClickListener { toggleTracking(); updateSummary() }
        }; layout.addView(tracking)
        sessionTotals = label("", 22f, true); layout.addView(sessionTotals)
        trackingStatus = label("", 14f); layout.addView(trackingStatus)
        layout.addView(button("Fill up") { chooseReadingSource(true) })
        layout.addView(button("Update dashboard reading") { chooseReadingSource(false) })
        undoReading = button("Undo last dashboard update / fill-up") { undoLastReading() }; layout.addView(undoReading)
        photoStatus = label("Photograph the odometer and average economy while parked. Recognition runs on your phone; confirm every reading before saving.", 13f)
        layout.addView(photoStatus)
        layout.addView(label("Nearby gas stations", 20f, true))
        findGasButton = button("Find gas") { findStations() }; layout.addView(findGasButton)
        stationResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; layout.addView(stationResults)
        stationSearch = FuelSearchController(this, null) { renderStations() }
        renderStations()
        layout.addView(button("Settings") { options() })
        val scroll = ScrollView(this).apply {
            addView(layout)
            setBackgroundColor(pageColor)
            clipToPadding = true
        }
        // Modern Android draws the window behind system bars. Keep the scroll viewport
        // inside their safe area, including landscape cutouts and the on-screen keyboard.
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            } else {
                val cutout = insets.displayCutout
                view.setPadding(maxOf(insets.systemWindowInsetLeft, cutout?.safeInsetLeft ?: 0),
                    maxOf(insets.systemWindowInsetTop, cutout?.safeInsetTop ?: 0),
                    maxOf(insets.systemWindowInsetRight, cutout?.safeInsetRight ?: 0),
                    maxOf(insets.systemWindowInsetBottom, cutout?.safeInsetBottom ?: 0))
            }
            insets
        }
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        else window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            (if (dark) 0 else View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR)
        setContentView(scroll)
        if (Build.VERSION.SDK_INT >= 30) {
            val light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            window.insetsController?.setSystemBarsAppearance(if (dark) 0 else light, light)
        }
        scroll.post { scroll.requestApplyInsets() }
        pendingPhoto?.let { uri -> handler.post { scanPhoto(uri) } }
        if (editingRide && pendingPhoto == null && !externalReading) handler.post {
            if (editFlow == "undo") undoLastReading(restoring = true) else chooseReadingSource(pendingFill)
        }
    }
    private fun renderStations() {
        val descriptions = stationSearch.stations.map { StationDirections.describe(this, it) }
        val key = "${stationSearch.canSearch}:${stationSearch.buttonText}:${stationSearch.message}:${stationSearch.freshness}:${stationSearch.stations}:$descriptions"
        if (key == stationRenderKey) return
        stationRenderKey = key
        findGasButton.isEnabled = stationSearch.canSearch
        findGasButton.text = stationSearch.buttonText
        stationResults.removeAllViews()
        stationResults.addView(label(stationSearch.message, 13f))
        if (stationSearch.stations.isNotEmpty()) stationResults.addView(label(stationSearch.freshness, 13f))
        stationSearch.stations.forEachIndexed { index, station ->
            val distance = descriptions[index]
            stationResults.addView(button("${station.name}\n$distance\nTap to open map") {
                try {
                    val name = Uri.encode(station.name)
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${station.latitude},${station.longitude}?q=${station.latitude},${station.longitude}($name)")))
                } catch (_: RuntimeException) { message("No navigation app available.") }
            })
        }
        if (stationSearch.attribution.isNotEmpty()) stationResults.addView(label("${stationSearch.attribution}\nopenstreetmap.org/copyright\nHours and gasoline availability unverified.", 12f))
    }
    private fun findStations() {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 2)
        else stationSearch.search()
    }
    private fun updateSummary() {
        if (::stationSearch.isInitialized) renderStations()
        refreshOptionStates()
        val estimate = FuelEstimateStore(this); val ride = RideStore(this)
        tracking.isChecked = ride.running
        frontTire.text = TpmsStore(this).wheel("front").replace(" • ", "\n")
        rearTire.text = TpmsStore(this).wheel("rear").replace(" • ", "\n")
        tireHealth.text = TpmsMonitor.health(this)
        frontTire.append("\nBattery: ${TpmsStore(this).battery("front")}")
        rearTire.append("\nBattery: ${TpmsStore(this).battery("rear")}")
        listOf("front" to frontTire, "rear" to rearTire).forEach { (wheel, view) ->
            val color = when (TpmsStore(this).warning(wheel)) { 2 -> Color.rgb(230, 55, 55); 1 -> Color.rgb(230, 185, 35); else -> if (dark) Color.rgb(65, 105, 80) else Color.rgb(125, 165, 140) }
            ((view.parent as View).background as? GradientDrawable)?.setStroke(dp(3), color)
        }
        val lastSession = ride.lastSummary.lineSequence()
            .filterNot { it.startsWith("GPS distance only") }.joinToString("\n")
        val sessionTime = lastSession.substringBefore('\n').removePrefix("Started ")
        val sessionDetails = lastSession.substringAfter('\n', "")
        sessionTotals.text = "Last session: $sessionTime" + if (sessionDetails.isNotEmpty()) "\n$sessionDetails" else ""
        trackingStatus.text = listOf(ride.healthText(), LivePosition.status(this), ride.status, ride.qualityText()).distinct().joinToString("\n")
        undoReading.isEnabled = FuelUndo(this).available
        fuel.text = estimate.fuelText(); range.text = estimate.rangeText()
        savedReading.text = estimate.readingText().substringBefore('\n')
        val fraction = FuelBars.fraction(estimate)
        gauge.fraction = fraction
        rangeGauge.progress = ((fraction ?: 0.0) * 1000).toInt()
        gauge.contentDescription = "Estimated fuel: ${estimate.fuelText()}"
        rangeGauge.contentDescription = "Estimated range: ${estimate.rangeText()}"
        fuelScale.text = if (fraction == null) "Fuel bar available after a full fill-up" else
            "Empty • ${AppUnits.get(this).number(fraction * 100, 0)}% remaining • Full: ${AppUnits.get(this).volume(estimate.filledCapacity!!, 1)}"
        rangeScale.text = if (fraction == null) "Range bar available after a full fill-up" else
            "0 ${AppUnits.get(this).distanceUnit} • Full-tank range: ${AppUnits.get(this).distance(estimate.filledCapacity!! * estimate.mpg!!, 0)} at current economy"
        details.text = "${estimate.distanceText()}\n${estimate.detailsText()}\n${estimate.confidenceText()}" +
            if (ride.manualMiles > 0) "\nIncludes ${AppUnits.get(this).distance(ride.manualMiles)} manually added" else ""
        quality.text = if (estimate.baselineOdometer == null) "Use Fill up after filling your tank completely."
            else if (ride.running || ride.gap || (ride.requestedRunning && !ride.running)) ride.qualityText()
            else ""
        quality.visibility = if (quality.text.isEmpty()) View.GONE else View.VISIBLE
    }
    private fun numeric(hint: String, initial: Double? = null) = EditText(this).apply {
        this.hint = hint; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        initial?.let { setText(it.toString()) }
    }
    private fun message(text: String) { Toast.makeText(this, text, Toast.LENGTH_LONG).show() }
    private fun toggleTracking() {
        if (RideStore(this).running) {
            RideControls.stop(this); updateSummary()
        } else startTracking()
    }
    private fun startTracking() {
        if (FuelEstimateStore(this).baselineOdometer == null) { message("Record a fill-up first."); return }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            startAfterPermission = true
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 5)
            return
        }
        try { startForegroundService(Intent(this, RideTrackingService::class.java)); message("Starting GPS tracking. Watch the status below.") }
        catch (e: RuntimeException) { RideStore(this).stop("Could not start GPS; retry with this screen open"); message("Could not start GPS tracking.") }
        updateSummary()
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 9) {
            if (TpmsMonitor.allowed(this)) message(TpmsMonitor.refresh(this, "phone"))
            else message("Enable Nearby devices access in Android app settings to read tire sensors.")
        }
        if (firstRunPermissions?.result(code) == true) { refreshOptionStates(); return }
        if (code == 8) {
            if (preciseAllowed()) handler.post { if (!isFinishing && !isDestroyed) backgroundLocationSetup() }
            else AlertDialog.Builder(this).setTitle("Precise location still needed")
                .setMessage("Open Permissions → Location, allow access and enable precise location. Then return and enable Background location allowed to finish setup.")
                .setNegativeButton("Cancel", null).setPositiveButton("Open settings") { _, _ -> appSettings() }.show()
        }
        if (code == 6 && Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            AlertDialog.Builder(this).setTitle("Finish background location setup")
                .setMessage("Open Permissions → Location → Allow all the time, keeping precise location enabled. While using the app is not enough for automatic Honda ride starts.")
                .setNegativeButton("Cancel", null).setPositiveButton("Open settings") { _, _ -> appSettings() }.show()
        }
        if (code == 2 && ::stationSearch.isInitialized) stationSearch.search()
        if (code == 7 && HondaAutoTracking.bluetoothEnabled(this)) {
            if (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                try { startForegroundService(Intent(this, BikeConnectionService::class.java)) }
                catch (_: RuntimeException) { message("Connection monitoring could not start. Reconnect the bike or retry while parked.") }
            } else message("Nearby devices permission is needed for Bluetooth recognition.")
        }
        refreshOptionStates()
        if (code == 5 && startAfterPermission) {
            startAfterPermission = false
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) startTracking()
            else message("Precise location is required for ride tracking.")
        }
    }
    private fun chooseReadingSource(fill: Boolean) {
        if (scanning) { message("A dashboard photo is already being read."); return }
        if (!fill && FuelEstimateStore(this).baselineOdometer == null) { message("Record a full fill-up first."); return }
        pendingFill = fill
        editFlow = "reading"
        pauseForEdit()
        AlertDialog.Builder(this).setTitle(if (fill) "Dashboard at full fill-up" else "Update dashboard reading")
            .setItems(if (fill) arrayOf("Take dashboard photo", "Choose an existing photo", "Enter readings manually")
                else arrayOf("Take dashboard / trip photo", "Choose an existing photo", "Enter missed distance and economy manually")) { _, selected ->
                when(selected) { 0 -> capturePhoto(); 1 -> selectPhoto(); else -> readingDialog(fill) }
            }.setNegativeButton("Cancel") { _, _ -> finishEdit() }.setOnCancelListener { finishEdit() }.show()
    }
    private fun capturePhoto() {
        try {
            val directory = File(cacheDir, "dashboard_photos").apply { mkdirs() }
            val file = File.createTempFile("dashboard-", ".jpg", directory)
            cameraPath = file.absolutePath
            val uri = FileProvider.getUriForFile(this, "$packageName.photos", file)
            val intent = Intent(this, DashboardCameraActivity::class.java).putExtra(MediaStore.EXTRA_OUTPUT, uri)
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.clipData = ClipData.newRawUri("Dashboard photo", uri)
            externalReading = true
            startActivityForResult(intent, 10)
        } catch(e: RuntimeException) { clearPhoto(); finishEdit(); message("Camera unavailable. Choose a photo or enter readings manually.") }
        catch(e: java.io.IOException) { clearPhoto(); finishEdit(); message("Could not prepare a photo file.") }
    }
    private fun selectPhoto() {
        externalReading = true
        try { startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE), 11) }
        catch(e: RuntimeException) { finishEdit(); message("Photo picker unavailable. Enter readings manually.") }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode !in listOf(10, 11)) return
        externalReading = false
        if (resultCode != RESULT_OK) { clearPhoto(); finishEdit(); return }
        val uri = if (requestCode == 10) cameraPath?.let { FileProvider.getUriForFile(this, "$packageName.photos", File(it)) } else data?.data
        if (uri == null) { clearPhoto(); finishEdit(); message("No photo was selected."); return }
        scanPhoto(uri)
    }
    private fun scanPhoto(uri: Uri) {
        if (scanning) return
        scanning = true; pendingPhoto = uri
        photoStatus.text = "Reading dashboard photo…"
        DashboardPhotoReader.read(applicationContext, uri) { result ->
            if (isFinishing || isDestroyed) return@read
            scanning = false
            result.onSuccess { scan ->
                photoStatus.text = "Review the photo readings below. Nothing is saved until you confirm."
                readingDialog(pendingFill, scan)
            }.onFailure {
                photoStatus.text = "Could not read this photo. You can enter both readings manually."
                clearPhoto(); readingDialog(pendingFill)
            }
        }
    }
    private fun clearPhoto() {
        cameraPath?.let { path ->
            val file = File(path)
            if (file.parentFile?.absolutePath == File(cacheDir, "dashboard_photos").absolutePath) file.delete()
        }
        cameraPath = null; pendingPhoto = null
    }
    override fun onSaveInstanceState(state: Bundle) {
        firstRunPermissions?.save(state)
        state.putBoolean("editingRide", editingRide); state.putBoolean("resumeRide", resumeRide); state.putLong("editStopSerial", editStopSerial)
        state.putString("editFlow", editFlow); state.putBoolean("externalReading", externalReading)
        state.putBoolean("pendingFill", pendingFill); state.putString("cameraPath", cameraPath); state.putString("pendingPhoto", pendingPhoto?.toString())
        super.onSaveInstanceState(state)
    }
    private fun readingDialog(fill: Boolean, scan: DashboardScan? = null) {
        val estimate = FuelEstimateStore(this)
        val useTrip = !fill && scan?.readings?.tripMiles != null
        val additional = !fill
        if (RideStore(this).running) { message("Stop GPS tracking before recording a dashboard reading."); return }
        if (!fill && estimate.baselineOdometer == null) { message("Record your first Fill up before updating readings."); return }
        var switchingReading = false
        var reviewDialog: AlertDialog? = null
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(8)) }
        if (scan != null) {
            box.addView(ImageView(this).apply { setImageBitmap(scan.preview); adjustViewBounds = true; maxHeight = dp(220) })
            box.addView(button("Adjust recognition boxes") {
                val imageUri = pendingPhoto ?: return@button
                val editor = DashboardCropView(this, scan.preview).apply { editing = true; setBackgroundColor(Color.BLACK) }
                val editorBox = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(label("Drag boxes to move. Drag the bottom-right dots to resize. Include economy/Trip and TOTAL labels with their digits. Saved boxes are also used by the camera guides.", 14f))
                    addView(button("Show / copy box coordinates") { DashboardCropAreas.showReport(this@MainActivity, editor.boxes, scan.preview.width, scan.preview.height) })
                    addView(editor, LinearLayout.LayoutParams(-1, (resources.displayMetrics.heightPixels * .50f).toInt()))
                }
                val editorDialog = AlertDialog.Builder(this).setTitle("Recognition areas").setView(editorBox)
                    .setPositiveButton("Save & read again") { _, _ ->
                        editor.save(); switchingReading = true; reviewDialog?.dismiss(); scanPhoto(imageUri)
                    }.setNegativeButton("Cancel", null).setNeutralButton("Reset", null).create()
                editorDialog.setOnShowListener {
                    editorDialog.window?.setLayout(-1, (resources.displayMetrics.heightPixels * .9f).toInt())
                    editorDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener { editor.reset() }
                }
                editorDialog.show()
            })
            box.addView(label(scan.readings.notes, 13f))
            box.addView(button("Show recognized text") { AlertDialog.Builder(this).setTitle("Recognized dashboard text")
                .setMessage(scan.text.ifBlank { "No text was recognized." }).setPositiveButton("Close", null).show() })
        }
        val units = AppUnits.get(this)
        box.addView(label(if (fill) "A full tank will be recorded at ${units.volume(estimate.capacity)}. Enter the odometer at this fill-up and average economy."
            else if (additional) "Update untracked distance and average economy together. Your fill-up baseline stays the same."
            else "Update the current distance reading and average economy. Your fill-up baseline stays the same.", 14f))
        val odo = numeric(if (additional) "Untracked distance (${units.distanceUnit})" else "Odometer (${units.distanceUnit})", (if (fill) scan?.readings?.odometerMiles else scan?.readings?.tripMiles)?.let(units::distanceValue))
        val mpg = numeric("Average economy (${units.economyUnit})", (scan?.readings?.averageMpg ?: estimate.mpg)?.let(units::economyValue))
        if (useTrip && scan?.readings?.averageMpg == null) box.addView(label("No economy reading in this trip photo; using your saved average economy. You can edit it below.", 13f))
        box.addView(label(if (additional) "Untracked distance (${units.distanceUnit})" else "Odometer (${units.distanceUnit})", 14f)); box.addView(odo)
        if (additional) box.addView(label("Enter only distance not already recorded by GPS. Enter 0 to update economy only. This distance is added to the existing total.", 13f))
        if (additional && scan != null) box.addView(label(
            if (useTrip) "${scan.readings.tripMeter} supplied the distance to add. Use this value only if that trip measures the untracked stretch; otherwise edit it to the missed distance. Do not add distance already recorded."
            else "No Trip A/B distance recognized. Enter the missed distance below. The TOTAL odometer is not a distance to add.", 13f))
        box.addView(label("Average economy (${units.economyUnit})", 14f)); box.addView(mpg)
        val preview = label("", 14f); box.addView(preview)
        fun refreshPreview() {
            val distance = odo.text.toString().toDoubleOrNull()?.let(units::distanceInput)
            val economy = mpg.text.toString().toDoubleOrNull()?.let(units::economyInput)
            val baseline = if (additional) 0.0 else distance
            val tank = if (fill) estimate.capacity else estimate.filledCapacity
            preview.text = if (distance != null && distance.isFinite() && distance in 0.0..1000000.0 &&
                economy != null && economy.isFinite() && economy in 1.0..200.0 && baseline != null && distance >= baseline && tank != null) {
                val miles = if (additional) estimate.miles + distance else distance - baseline
                val remaining = EstimatedFuel.gallons(tank, miles, economy)!!
                "After saving: ${units.distance(miles)} since fill-up\nEstimated fuel: ${units.volume(remaining)} • Range: ${units.distance(remaining * economy, 0)}" +
                    if (fill) "\nFill up records a full tank and resets distance to zero." else "\nThis adds the untracked stretch and applies the updated economy."
            } else "Not saved: check both readings below before saving."
        }
        val watcher = object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { refreshPreview() }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        }
        odo.addTextChangedListener(watcher); mpg.addTextChangedListener(watcher); refreshPreview()
        val error = label("", 13f); box.addView(error)
        val dialog = AlertDialog.Builder(this).setTitle(if (fill) "Record full fill-up" else "Confirm dashboard reading")
            .setView(ScrollView(this).apply { addView(box) }).setNegativeButton("Cancel", null).setPositiveButton("Save readings", null).create()
        reviewDialog = dialog
        dialog.setOnDismissListener { if (!switchingReading && !isChangingConfigurations) { clearPhoto(); finishEdit() } }
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val distance = odo.text.toString().toDoubleOrNull()?.let(units::distanceInput); val economy = mpg.text.toString().toDoubleOrNull()?.let(units::economyInput)
                if (distance == null || economy == null || !distance.isFinite() || !economy.isFinite() || distance !in 0.0..1000000.0 || economy !in 1.0..200.0) {
                    error.text = "Enter valid distance (${units.distanceUnit}) and average economy (${units.economyUnit})."
                } else if (RideStore(this).running) {
                    error.text = "GPS tracking started while reviewing. Stop it before saving."
                } else {
                    if (fill) estimate.fillUp(distance, economy) else estimate.updateMissedDistance(distance, economy)
                    photoStatus.text = "${if (fill) "Full fill-up" else "Dashboard reading"} saved. ${estimate.distanceText()} • ${estimate.fuelText()} • Range ${estimate.rangeText()}"
                    message("Readings saved: ${if (additional) "added distance" else "odometer"} ${units.distance(distance)}, average ${units.economy(economy)}.")
                    updateSummary(); dialog.dismiss()
                }
            }
        }
        dialog.show()
    }
    private fun undoLastReading(restoring: Boolean = false) {
        if (!FuelUndo(this).available || (editingRide && !restoring) || scanning) return
        editFlow = "undo"
        pauseForEdit()
        val dialog = AlertDialog.Builder(this).setTitle("Undo last reading?")
            .setMessage("Restore the previous fill-up, economy and distance correction. GPS distance recorded since that change will be kept.")
            .setNegativeButton("Cancel", null).setPositiveButton("Undo") { _, _ ->
                try { FuelUndo(this).restore(); photoStatus.text = "Last dashboard change undone"; updateSummary() }
                catch (_: RuntimeException) { message("Could not undo the last reading") }
            }.create()
        dialog.setOnDismissListener { if (!isChangingConfigurations) finishEdit() }
        dialog.show()
    }
    private fun advanced() {
        // TPMS scanning stays in a foreground diagnostic screen until readings are verified.
        val tpmsIntent = Intent(this, TpmsScanActivity::class.java)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), dp(12)) }
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
            val report = getSharedPreferences("station_diagnostics", MODE_PRIVATE).getString("report", "No station request recorded yet.") ?: "No report"
            AlertDialog.Builder(this).setTitle("Latest station search")
                .setMessage(report).setPositiveButton("Close", null)
                .setNeutralButton("Copy report") { _, _ ->
                    getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Station search report", report))
                    message("Report copied")
                }.show()
        })
        box.addView(button("Saved GPS / compass test") {
            val report = getSharedPreferences("host_sensor_test", MODE_PRIVATE).getString("report", "Run GPS / compass test from the Android Auto app first.") ?: "No report"
            val text = label(report, 14f).apply { setTextIsSelectable(true); setPadding(dp(20), dp(12), dp(20), dp(12)) }
            AlertDialog.Builder(this).setTitle("GPS / compass test report")
                .setView(ScrollView(this).apply { addView(text) }).setPositiveButton("Close", null)
                .setNeutralButton("Copy report") { _, _ ->
                    getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Host GPS / compass test", report))
                    message("Report copied.")
                }.show()
        })
        AlertDialog.Builder(this).setTitle("Advanced").setView(ScrollView(this).apply { addView(box) }).setPositiveButton("Close", null).show()
    }
    private fun options() {
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
        box.addView(button("Tire pressure warnings & monitoring") { TpmsAlerts.settings(this) })
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
                        getSharedPreferences("station_search_options", MODE_PRIVATE).edit().putInt("autoPercent", value).apply()
                        autoStations.text = "Auto-find gas at $value% fuel"; dialog.dismiss()
                    }
                }
            }
            dialog.show()
        }
        box.addView(autoStations)
        box.addView(label("Station searches send your location to Overpass. Map data: © OpenStreetMap contributors.", 13f))
        box.addView(label("Notifications", 20f, true))
        val noticePrefs = getSharedPreferences("fuel_notice", MODE_PRIVATE)
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
            isChecked = FuelSummaryNotice.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, enabled ->
                getSharedPreferences("fuel_notice", MODE_PRIVATE).edit().putBoolean("enabled", enabled).apply()
                if (!enabled) FuelSummaryNotice.cancel(this@MainActivity)
            }
        })
        val noticeInterval = button("Remind every: ${FuelSummaryNotice.interval(this)} minutes") { }
        noticeInterval.setOnClickListener {
            val intervals = intArrayOf(5, 10, 15, 30)
            AlertDialog.Builder(this).setTitle("Fuel and range reminder interval")
                .setItems(intervals.map { "$it minutes" }.toTypedArray()) { _, selected ->
                    getSharedPreferences("fuel_notice", MODE_PRIVATE).edit().putInt("minutes", intervals[selected]).apply()
                    noticeInterval.text = "Remind every: ${intervals[selected]} minutes"
                }.show()
        }
        box.addView(noticeInterval)
        box.addView(label("Reminders appear during tracking. Default: every 10 minutes.", 13f))
        box.addView(label("Appearance", 20f, true))
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
                getSharedPreferences("appearance", MODE_PRIVATE).edit().putString("mode", arrayOf("system", "light", "dark")[selected]).apply()
                recreate()
            }.show()
        })
        box.addView(label("Automatic Session Start/Stop", 20f, true))
        box.addView(label("The app tries to start tracking when your head-unit connects to Android Auto. Keep connection monitoring on and allow location all the time.", 13f))
        val disconnectSpeed = button("Continue above: ${units.speed(RideDisconnect.thresholdMph(this).toDouble())}") { }
        disconnectSpeed.isEnabled = RideDisconnect.enabled(this)
        box.addView(Switch(this).apply {
            text = "Continue tracking after head-unit or Bluetooth disconnect"
            isChecked = RideDisconnect.enabled(this@MainActivity)
            setOnCheckedChangeListener { _, enabled ->
                RideDisconnect.setEnabled(this@MainActivity, enabled)
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
            val name = EditText(this).apply { setSingleLine(true); setText(HondaAutoTracking.deviceName(this@MainActivity)) }
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
        box.addView(button("Advanced") { advanced() })
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
    private fun about() {
        val installed = packageManager.getPackageInfo(packageName, 0)
        AlertDialog.Builder(this).setTitle("About ADV Ride Helper")
            .setMessage("Version ${installed.versionName ?: "Unknown"}\nBuild code ${installed.longVersionCode}\n\nFuel and range estimates from confirmed dashboard readings and phone GPS distance.\n\nPackage: $packageName")
            .setPositiveButton("Close", null).show()
    }
    private fun headUnitInfo() {
        val report = label(HeadUnitDiagnostics(this).report(), 14f).apply {
            setTextIsSelectable(true); setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val dialog = AlertDialog.Builder(this).setTitle("Saved head-unit info")
            .setView(ScrollView(this).apply { addView(report) }).setPositiveButton("Close", null)
            .setNeutralButton("Copy report", null).setNegativeButton("Refresh", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                val latest = HeadUnitDiagnostics(this).report(); report.text = latest
                getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Head-unit diagnostic history", latest))
                message("Diagnostic report copied.")
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener { report.text = HeadUnitDiagnostics(this).report() }
        }
        dialog.show()
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent) }
    override fun onResume() {
        super.onResume(); if (recoveryMode) return
        handler.post(refresh)
        handler.post { firstRunPermissions?.resume() }
        if (TpmsAlerts.backgroundEnabled(this) && TpmsMonitor.allowed(this)) {
            TpmsRecovery.startFromUi(this)
        }
        if (intent.action == RideControls.START_ON_PHONE && firstRunPermissions?.complete != false) {
            intent.action = null
            handler.post { if (!isFinishing && !isDestroyed) startTracking() }
        }
    }
    override fun onPause() { TpmsMonitor.stop("phone"); handler.removeCallbacks(refresh); super.onPause() }
    override fun onStart() { super.onStart(); if (::stationSearch.isInitialized) stationSearch.start() }
    override fun onStop() { if (::stationSearch.isInitialized) stationSearch.stop(); super.onStop() }
    override fun onDestroy() { if (::stationSearch.isInitialized) stationSearch.close(); super.onDestroy() }
}








