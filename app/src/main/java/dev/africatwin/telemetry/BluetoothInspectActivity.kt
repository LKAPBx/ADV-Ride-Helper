package dev.africatwin.telemetry

import android.Manifest
import android.app.*
import android.bluetooth.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.*
import android.view.WindowInsets
import android.widget.*

/** SDP only. Does not open RFCOMM/GATT sessions or write to application services. */
class BluetoothInspectActivity : Activity() {
    private lateinit var reportView: TextView
    private lateinit var inspect: Button
    private val handler = Handler(Looper.getMainLooper())
    private var target: BluetoothDevice? = null
    private var registered = false
    private var active = false
    private var generation = 0
    private val lines = mutableListOf<String>()
    private val prefs get() = getSharedPreferences("bluetooth_service_report", MODE_PRIVATE)
    private val timeout = Runnable {
        lines.add("SDP response timed out after 25 seconds. Cached services may be incomplete; this does not prove no other services exist.")
        finishRequest()
    }
    private fun publish() {
        val text = lines.joinToString("\n")
        reportView.text = text
        prefs.edit().putString("report", text).apply()
    }
    private fun finishRequest() { handler.removeCallbacks(timeout); inspect.isEnabled = true; publish() }
    override fun onCreate(state: Bundle?) {
        val appearance = getSharedPreferences("appearance", MODE_PRIVATE).getString("mode", "system")
        val dark = appearance == "dark" || (appearance == "system" && resources.configuration.uiMode and 48 == 32)
        setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16) }
        fun label(text: String, size: Float) = TextView(this).apply { this.text = text; textSize = size; setTextColor(if (dark) Color.WHITE else Color.BLACK); setPadding(0, 12, 0, 12) }
        fun button(text: String, action: () -> Unit) = Button(this).apply { this.text = text; PhoneButtons.style(this, dark); setOnClickListener { action() } }
        box.addView(label("Head-unit Bluetooth services", 24f))
        box.addView(label("Pair and connect the head unit first. This check requests its service list and audio/headset connection status. It does not read engine data or send commands to discovered services.", 16f))
        inspect = button("Inspect services") { begin() }; box.addView(inspect)
        box.addView(button("Copy report") {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Head-unit Bluetooth services", reportView.text))
            Toast.makeText(this, "Report copied", Toast.LENGTH_SHORT).show()
        })
        reportView = label(prefs.getString("report", "No inspection yet. Tap Inspect services.").orEmpty(), 15f).apply { setTextIsSelectable(true) }
        box.addView(reportView); box.addView(button("Close") { finish() })
        val scroll = ScrollView(this).apply { addView(box); setBackgroundColor(if (dark) 0xff122019.toInt() else 0xfff1f6f3.toInt()) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        setContentView(scroll); scroll.requestApplyInsets()
    }
    private fun begin() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.BLUETOOTH_CONNECT), 1); return
        }
        try {
            val adapter = getSystemService(BluetoothManager::class.java).adapter
            if (adapter == null || !adapter.isEnabled) { reportView.text = "Turn on Bluetooth and connect the head unit, then retry."; return }
            val match = HondaAutoTracking.deviceName(this)
            val devices = adapter.bondedDevices.filter { HondaIdentity.bluetoothMatches(it.name, match) }.sortedBy { it.address }
            if (devices.isEmpty()) { reportView.text = "No paired device matches '$match'. Pair the head unit or change Head-unit Bluetooth name in Settings."; return }
            if (devices.size == 1) inspectDevice(adapter, devices.first())
            else AlertDialog.Builder(this).setTitle("Select matching head unit")
                .setItems(devices.map { "${it.name} • ${it.address}" }.toTypedArray()) { _, index -> inspectDevice(adapter, devices[index]) }
                .setNegativeButton("Cancel", null).show()
        } catch (error: RuntimeException) { reportView.text = "Bluetooth inspection unavailable: ${error.javaClass.simpleName}. Check Nearby devices access." }
    }
    private fun inspectDevice(adapter: BluetoothAdapter, device: BluetoothDevice) {
        try {
            generation++; val run = generation
            handler.removeCallbacks(timeout); target = device; inspect.isEnabled = false; lines.clear()
            val version = packageManager.getPackageInfo(packageName, 0).versionName
            lines.add("ADV Ride Helper $version • Bluetooth service report\n${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}")
            lines.add("Name match: ${HondaAutoTracking.deviceName(this)}\nDevice: ${device.name}\nAddress: ${device.address}\nBond state: ${if (device.bondState == BluetoothDevice.BOND_BONDED) "Paired" else device.bondState}\nTransport type: ${device.type} (1=Classic, 2=LE, 3=Dual)\nDevice class: ${device.bluetoothClass?.deviceClass?.toString(16) ?: "unavailable"}")
            lines.add("Initial cached UUIDs:")
            addUuids(device.uuids?.toList().orEmpty())
            lines.add("Requesting SDP; audio/headset profile states pending…")
            listOf(BluetoothProfile.HEADSET, BluetoothProfile.A2DP).forEach { profile ->
                val requested = adapter.getProfileProxy(this, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        try {
                            if (!active || generation != run) return
                            val state = when (proxy.getConnectionState(device)) {
                                BluetoothProfile.STATE_CONNECTED -> "Connected"
                                BluetoothProfile.STATE_CONNECTING -> "Connecting"
                                BluetoothProfile.STATE_DISCONNECTING -> "Disconnecting"
                                else -> "Disconnected"
                            }
                            lines.add("${if (p == BluetoothProfile.HEADSET) "Headset/Hands-free" else "A2DP audio"}: $state")
                            publish()
                        } catch (error: RuntimeException) { if (active && generation == run) { lines.add("Profile query failed: ${error.javaClass.simpleName}"); publish() } }
                        finally { adapter.closeProfileProxy(p, proxy) }
                    }
                    override fun onServiceDisconnected(p: Int) = Unit
                }, profile)
                if (!requested) lines.add("Profile $profile: query could not be started")
            }
            val requested = device.fetchUuidsWithSdp()
            lines.add("SDP request accepted by Android: $requested")
            lines.add("Android can return cached UUIDs even for an SDP callback. No guaranteed complete/fresh service list. Custom UUIDs do not establish telemetry support. This is not a BLE GATT characteristic inventory.")
            publish()
            if (requested) handler.postDelayed(timeout, 25_000) else finishRequest()
        } catch (error: RuntimeException) { lines.add("Inspection failed: ${error.javaClass.simpleName}"); finishRequest() }
    }
    private fun addUuids(uuids: List<ParcelUuid>) {
        if (uuids.isEmpty()) lines.add("  None returned (not evidence of no services)")
        uuids.distinct().sortedBy { it.toString() }.forEach { lines.add("  $it — ${BluetoothServiceNames.describe(it.toString())}") }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_UUID || !active || inspect.isEnabled) return
            try {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                if (device.address != target?.address) return
                lines.add("SDP UUID callback received (may be cached):")
                addUuids(intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID)?.filterIsInstance<ParcelUuid>().orEmpty())
                finishRequest()
            } catch (error: RuntimeException) { lines.add("SDP response unavailable: ${error.javaClass.simpleName}"); finishRequest() }
        }
    }
    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        if (code == 1) handler.post {
            if (Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) { if (active) begin() }
            else reportView.text = "Allow Nearby devices in Android app settings, then retry."
        }
    }
    override fun onStart() {
        super.onStart(); active = true
        // Bluetooth broadcasts can originate from a privileged non-system-UID process.
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_UUID), Context.RECEIVER_EXPORTED)
        else registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_UUID))
        registered = true
    }
    override fun onStop() {
        active = false; generation++; handler.removeCallbacks(timeout)
        if (!inspect.isEnabled) { lines.add("Inspection interrupted when screen closed; tap Inspect to retry."); finishRequest() }
        if (registered) { unregisterReceiver(receiver); registered = false }
        super.onStop()
    }
}
