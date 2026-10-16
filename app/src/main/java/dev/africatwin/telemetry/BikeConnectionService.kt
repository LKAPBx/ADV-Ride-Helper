package dev.africatwin.telemetry

import android.app.*
import android.bluetooth.*
import android.content.*
import android.content.pm.ServiceInfo
import android.os.*
import androidx.car.app.connection.CarConnection
import androidx.lifecycle.Observer

/** Keeps connection observation alive during the Bluetooth-to-projection boot interval. */
class BikeConnectionService : Service() {
    companion object { internal var current: BikeConnectionService? = null; private set }
    private var alive = true
    private lateinit var connection: CarConnection
    private var bikeConnected = false
    private var projection = false
    private var attempted = false
    internal var suppressed = false
        private set
    fun suppressForConnection() { suppressed = true }
    private val handler = Handler(Looper.getMainLooper())
    private val observer = Observer<Int> { state ->
        val connected = state == CarConnection.CONNECTION_TYPE_PROJECTION
        val disconnected = projection && !connected
        if (disconnected) { RideDisconnect.handle(this, "Android Auto"); attempted = false; suppressed = false }
        if (connected && !projection) RideDisconnect.reconnected(this)
        projection = connected
        if (disconnected && !bikeConnected) stopSelf()
        evaluate()
    }
    private fun evaluate() {
        if (alive && bikeConnected && projection && !attempted && !suppressed && HondaAutoTracking.bluetoothEnabled(this)) {
            attempted = true
            HondaAutoTracking.attempt(this, "Matched Bluetooth device + Android Auto")
        }
    }
    override fun onBind(intent: Intent?) = null
    override fun onCreate() {
        super.onCreate()
        current = this
        connection = CarConnection(this)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("bike_connection", "Automatic bike connection monitoring", NotificationManager.IMPORTANCE_LOW))
            val open = PendingIntent.getActivity(this, 21, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val notice = Notification.Builder(this, "bike_connection").setSmallIcon(R.drawable.ic_app)
                .setContentTitle("Automatic tracking ready")
                .setContentText("Watching ${HondaAutoTracking.deviceName(this)} and Android Auto")
                .setContentIntent(open).setOngoing(true).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(47, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            else startForeground(47, notice)
            if (intent?.getBooleanExtra("refreshDeviceMatch", false) == true) { bikeConnected = false; attempted = false }
            if (intent?.getBooleanExtra("confirmedBike", false) == true) bikeConnected = true
            if (!bikeConnected) checkProfiles()
            connection.type.observeForever(observer)
            evaluate()
        } catch (e: RuntimeException) {
            RideStore(this).status("Connection monitor could not start (${e.javaClass.simpleName}); check Bluetooth permission")
            stopSelf()
        }
        return START_NOT_STICKY
    }
    private fun checkProfiles() {
        val adapter = getSystemService(BluetoothManager::class.java).adapter ?: return stopSelf()
        val matches = mutableSetOf<Int>()
        var replies = 0
        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
                try {
                    if (!alive) return
                    if (proxy.connectedDevices.any { HondaIdentity.bluetoothMatches(it.name, HondaAutoTracking.deviceName(this@BikeConnectionService)) }) matches.add(profile)
                    bikeConnected = matches.isNotEmpty(); evaluate()
                } catch (_: SecurityException) { RideStore(this@BikeConnectionService).status("Bluetooth permission required") }
                finally { adapter.closeProfileProxy(profile, proxy); replies++ }
                if (replies == 2 && !bikeConnected) stopSelf()
            }
            override fun onServiceDisconnected(profile: Int) = Unit
        }
        adapter.getProfileProxy(this, listener, BluetoothProfile.HEADSET)
        adapter.getProfileProxy(this, listener, BluetoothProfile.A2DP)
        handler.postDelayed({ if (!bikeConnected) stopSelf() }, 15_000)
    }
    override fun onDestroy() {
        alive = false
        if (current === this) current = null
        connection.type.removeObserver(observer)
        handler.removeCallbacksAndMessages(null)
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    fun bikeDisconnected() {
        bikeConnected = false
        RideDisconnect.handle(this, "Bluetooth device", stopWhenStationary = !projection)
        // Wireless projection may outlive the Bluetooth transport; stop on Auto loss.
        if (!projection) stopSelf()
    }
}

