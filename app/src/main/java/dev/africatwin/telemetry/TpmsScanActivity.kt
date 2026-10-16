package dev.africatwin.telemetry

import android.Manifest
import android.app.*
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.view.*
import android.widget.*
import java.util.Locale

/** Foreground diagnostic only: listens to advertisements; never pairs, connects or writes. */
class TpmsScanActivity : Activity() {
    private data class Seen(val id: String, var name: String, var candidate: FoboCandidate?, var time: Long,
        var raw: String, var format: String, var rssi: Int)
    private val devices = linkedMapOf<String, Seen>()
    private val discovered = linkedMapOf<String, String>()
    private val cards = mutableMapOf<String, TextView>()
    private val packets = ArrayDeque<String>()
    private val handler = Handler(Looper.getMainLooper())
    private var scanner: BluetoothLeScanner? = null
    private var scanning = false
    private var active = false
    private var startAfterPermission = false
    private var count = 0
    private var started = 0L
    private lateinit var status: TextView
    private lateinit var assigned: TextView
    private lateinit var results: LinearLayout
    private lateinit var scan: Button
    private var dark = false
    private val prefs get() = getSharedPreferences("tpms", MODE_PRIVATE)
    private fun text(value: String, size: Float = 16f) = TextView(this).apply {
        text = value; textSize = size; setTextColor(if (dark) Color.WHITE else Color.BLACK); setPadding(12, 12, 12, 12)
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value; PhoneButtons.style(this, dark); setOnClickListener { action() }
    }
    override fun onCreate(state: Bundle?) {
        val appearance = getSharedPreferences("appearance", MODE_PRIVATE).getString("mode", "system")
        dark = appearance == "dark" || (appearance == "system" && resources.configuration.uiMode and 48 == 32)
        setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(20, 12, 20, 12) }
        root.addView(text("FOBO Bike 2 sensor test", 24f))
        root.addView(text("Experimental readings. Compare pressure and temperature with the FOBO app before relying on them. Scan while parked near your sensors. This test listens only; it does not change sensor settings.", 15f))
        scan = button("Scan TPMS sensors") { if (scanning) stop("Scan stopped") else start() }; root.addView(scan)
        status = text("Ready. Scanning stops after 2 minutes or when you leave this screen."); root.addView(status)
        assigned = text(""); root.addView(assigned)
        root.addView(button("Assign front sensor") { chooseSensor("front") })
        root.addView(button("Assign rear sensor") { chooseSensor("rear") })
        root.addView(button("Copy scan report") {
            val report = "ADV Ride Helper FOBO diagnostic — source/pressure reference unverified\n${status.text}\n${assigned.text}\n" +
                devices.values.joinToString("\n") { "${it.name} ${it.id}: ${description(it)}\n${it.format}: ${it.raw}" } +
                "\nRecent BLE packets (local diagnostic):\n" + packets.joinToString("\n")
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("FOBO BLE scan", report))
            Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
        })
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; root.addView(results)
        root.addView(button("Close") { finish() })
        val scroll = ScrollView(this).apply { addView(root); setBackgroundColor(if (dark) 0xff122019.toInt() else 0xfff1f6f3.toInt()) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        setContentView(scroll); scroll.requestApplyInsets(); render()
    }
    private fun required() = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun start() {
        if (required().any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            startAfterPermission = true
            requestPermissions(required(), 1); return
        }
        try {
            val adapter = getSystemService(BluetoothManager::class.java).adapter
            if (adapter == null || !adapter.isEnabled) { status.text = "Turn on Bluetooth, then try again."; return }
            if (Build.VERSION.SDK_INT < 31 && !getSystemService(android.location.LocationManager::class.java).isLocationEnabled) {
                status.text = "Turn on phone location services for BLE scanning, then try again."; return
            }
            scanner = adapter.bluetoothLeScanner
            if (scanner == null) { status.text = "BLE scanning is unavailable."; return }
            count = 0; started = SystemClock.elapsedRealtime(); scanning = true
            scanner!!.startScan(null, ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
            scan.text = "Stop scan"; status.text = "Listening for FOBO broadcasts…"
        } catch (error: RuntimeException) { stop("Could not scan: ${error.javaClass.simpleName}. Check Bluetooth permissions.") }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == 1) {
            if (required().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
                if (active) { startAfterPermission = false; start() }
            } else {
                startAfterPermission = false
                status.text = "BLE permission missing. Enable Nearby devices (or Location on older Android) in Android app settings."
            }
        }
    }
    private val callback = object : ScanCallback() {
        override fun onScanResult(type: Int, result: ScanResult) { handler.post { if (scanning && active) accept(result) } }
        override fun onBatchScanResults(results: MutableList<ScanResult>) { results.forEach { onScanResult(0, it) } }
        override fun onScanFailed(code: Int) { handler.post { stop("BLE scan failed (code $code). Stop and try again after a few seconds.") } }
    }
    private fun accept(result: ScanResult) {
        try {
            val record = result.scanRecord ?: return
            count++
            val id = result.device.address
            val name = record.deviceName ?: "Unnamed BLE device"
            if (discovered.size < 100 || discovered.containsKey(id)) discovered[id] = name
            val manufacturer = record.getManufacturerSpecificData(0x0127)
            val service = record.getServiceData(ParcelUuid.fromString("0000012a-0000-1000-8000-00805f9b34fb"))
            val bike2 = record.getServiceData(ParcelUuid.fromString("00000126-0000-1000-8000-00805f9b34fb"))
            val data = bike2 ?: service ?: manufacturer
            fun hex(bytes: ByteArray) = bytes.take(128).joinToString("") { "%02X".format(it.toInt() and 255) }
            packets.addLast("$id $name RSSI=${result.rssi} ${record.bytes?.let(::hex).orEmpty()}")
            while (packets.size > 60) packets.removeFirst()
            if (data == null && !name.contains("FOBO", true) && id != prefs.getString("front", "") && id != prefs.getString("rear", "")) return
            if (!devices.containsKey(id) && devices.size >= 40) return
            val candidate = if (bike2 != null) FoboDecoder.decodeBike2Pressure(bike2, id)
                else data?.let { FoboDecoder.decode(it, service != null) }
            val age = SystemClock.elapsedRealtimeNanos() - result.timestampNanos
            if (candidate != null && age in 0L..120_000_000_000L)
                TpmsStore(this).record(id, candidate, System.currentTimeMillis() - age / 1_000_000)
            val seen = Seen(id, name, candidate, result.timestampNanos / 1_000_000,
                data?.let(::hex) ?: record.bytes?.let(::hex).orEmpty(),
                if (bike2 != null) "Service 0x0126 • provisional temperature" else if (service != null) "Service 0x012A" else if (manufacturer != null) "Manufacturer 0x0127" else "Unknown format", result.rssi)
            devices[id] = seen
            if (!cards.containsKey(id)) {
                val card = text(""); results.addView(card); cards[id] = card
                card.setOnClickListener { chooseSensor(null, id) }
                results.addView(button("Assign front / rear") {
                    AlertDialog.Builder(this).setTitle("Assign $name")
                        .setItems(arrayOf("Front tire", "Rear tire", "Remove assignment")) { _, selected ->
                            val edit = prefs.edit()
                            listOf("front", "rear").filter { prefs.getString(it, "") == id }.forEach { edit.remove(it) }
                            if (selected < 2) edit.putString(if (selected == 0) "front" else "rear", id)
                            TpmsAlerts.assignmentsChanged(this)
                            edit.apply(); TpmsRecovery.arm(this, true); render()
                        }.show()
                })
            }
            render()
        } catch (_: RuntimeException) { status.text = "Could not read a BLE packet. Check Nearby devices access." }
    }
    private fun assign(wheel: String?, id: String) {
        TpmsAlerts.assignmentsChanged(this)
        val edit = prefs.edit()
        listOf("front", "rear").filter { prefs.getString(it, "") == id }.forEach { edit.remove(it) }
        if (wheel != null) edit.putString(wheel, id)
        edit.apply(); TpmsRecovery.arm(this, true); render()
        Toast.makeText(this, if (wheel == null) "Assignment removed" else "Sensor assigned to $wheel tire", Toast.LENGTH_SHORT).show()
    }
    private fun chooseSensor(wheel: String?, selectedId: String? = null) {
        if (selectedId != null) {
            AlertDialog.Builder(this).setTitle("Assign $selectedId")
                .setItems(arrayOf("Front tire", "Rear tire", "Remove assignment")) { _, index ->
                    assign(if (index == 0) "front" else if (index == 1) "rear" else null, selectedId)
                }.show()
            return
        }
        val target = wheel ?: return
        val ids = discovered.keys.sortedWith(compareByDescending<String> { devices[it]?.candidate != null }.thenBy { it })
        fun selectionLabels() = ids.map { id ->
            val assignedTo = listOf("front", "rear").firstOrNull { prefs.getString(it, "") == id }
            val identity = "${discovered[id]} • $id" + (assignedTo?.let { " • Assigned: $it" } ?: "")
            val reading = devices[id]?.let(::description) ?: "Pressure / temperature unavailable • format unrecognized"
            "$identity\n$reading"
        } + "Clear $target assignment"
        val adapter = ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, selectionLabels().toMutableList())
        val dialog = AlertDialog.Builder(this).setTitle("Select $target sensor")
            .setAdapter(adapter) { _, index ->
                if (index < ids.size) assign(target, ids[index]) else { prefs.edit().remove(target).apply(); TpmsRecovery.arm(this, true); render() }
            }.setNeutralButton("Enter sensor ID") { _, _ -> enterSensorId(target) }
            .setNegativeButton("Cancel", null).create()
        val refreshChoices = object : Runnable {
            override fun run() {
                if (!dialog.isShowing) return
                adapter.setNotifyOnChange(false)
                adapter.clear(); adapter.addAll(selectionLabels()); adapter.notifyDataSetChanged()
                handler.postDelayed(this, 1000)
            }
        }
        dialog.setOnDismissListener { handler.removeCallbacks(refreshChoices) }
        dialog.show(); handler.postDelayed(refreshChoices, 1000)
        if (ids.isEmpty()) Toast.makeText(this, "Scan first, or enter the sensor ID from your report.", Toast.LENGTH_LONG).show()
    }
    private fun enterSensorId(wheel: String) {
        val input = EditText(this).apply { setSingleLine(true); hint = "00:15:88:0B:9E:88"; setText(prefs.getString(wheel, "")) }
        val dialog = AlertDialog.Builder(this).setTitle("$wheel sensor ID")
            .setMessage("Enter the Bluetooth address from the scan report. Assignment does not verify the reading or identify an unknown format.")
            .setView(input).setPositiveButton("Assign", null).setNegativeButton("Cancel", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val id = input.text.toString().trim().uppercase(Locale.US)
                if (!Regex("(?:[0-9A-F]{2}:){5}[0-9A-F]{2}").matches(id)) input.error = "Enter six pairs of letters/numbers separated by colons"
                else { assign(wheel, id); dialog.dismiss() }
            }
        }
        dialog.show()
    }
    private fun description(seen: Seen): String {
        val age = (SystemClock.elapsedRealtime() - seen.time).coerceAtLeast(0) / 1000
        val value = seen.candidate?.let {
            "${AppUnits.get(this).pressure(it.psi)} • ${AppUnits.get(this).temperature(it.celsius)}"
        }
            ?: "No supported reading in this packet"
        return "$value\n${age}s old${if (age > 30 || !scanning) " • not live" else ""} • UNVERIFIED • RSSI ${seen.rssi}"
    }
    private fun render() {
        listOf("front", "rear").joinToString("\n") { wheel ->
            val id = prefs.getString(wheel, "")
            "${wheel.replaceFirstChar { it.uppercase() }}: " + (devices[id]?.let(::description) ?: if (id.isNullOrEmpty()) "Not assigned" else "Assigned; waiting for a new broadcast ($id)")
        }.let { assigned.text = it }
        devices.forEach { (id, seen) -> cards[id]?.text = "${seen.name}\n$id\n${description(seen)}" }
    }
    private fun stop(message: String) {
        scanning = false
        try { scanner?.stopScan(callback) } catch (_: RuntimeException) { }
        scanner = null; scan.text = "Scan TPMS sensors"; status.text = message; render()
    }
    private val tick = object : Runnable {
        override fun run() {
            if (!active) return
            if (scanning && SystemClock.elapsedRealtime() - started >= 120_000) stop("Scan complete. $count BLE packets received; ${devices.size} candidate devices.")
            else if (scanning) status.text = "Scanning • $count BLE packets • ${devices.size} candidates • ${(120_000 - (SystemClock.elapsedRealtime() - started)) / 1000}s remaining"
            render(); handler.postDelayed(this, 1000)
        }
    }
    override fun onResume() {
        super.onResume(); active = true; handler.post(tick)
        if (startAfterPermission && required().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }) {
            startAfterPermission = false; start()
        }
    }
    override fun onPause() { active = false; stop("Scan paused. Tap Scan to listen again."); handler.removeCallbacks(tick); super.onPause() }
}
