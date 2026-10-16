package dev.africatwin.telemetry

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.*
import android.content.pm.PackageManager
import android.os.Build

class BikeConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BluetoothDevice.ACTION_ACL_CONNECTED && intent.action != BluetoothDevice.ACTION_ACL_DISCONNECTED) return
        if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
        try {
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            if (!HondaIdentity.bluetoothMatches(device.name, HondaAutoTracking.deviceName(context))) return
            if (intent.action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
                val monitor = BikeConnectionService.current
                if (monitor != null) monitor.bikeDisconnected() else RideDisconnect.handle(context, "Bluetooth device")
            } else if (HondaAutoTracking.bluetoothEnabled(context)) {
                context.startForegroundService(Intent(context, BikeConnectionService::class.java).putExtra("confirmedBike", true))
            }
        } catch (e: RuntimeException) {
            RideStore(context).status("Bike connection monitor blocked (${e.javaClass.simpleName}); open phone app while parked")
        }
    }
}

