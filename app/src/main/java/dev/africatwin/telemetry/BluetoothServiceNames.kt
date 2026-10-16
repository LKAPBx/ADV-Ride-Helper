package dev.africatwin.telemetry

internal object BluetoothServiceNames {
    fun describe(uuid: String): String {
        val value = uuid.lowercase(java.util.Locale.US)
        if (!Regex("0000[0-9a-f]{4}-0000-1000-8000-00805f9b34fb").matches(value))
            return "Custom/unknown UUID — protocol unknown"
        return when (value.substring(4, 8)) {
            "1101" -> "Serial Port — protocol unknown; not evidence of telemetry"
            "1108" -> "Headset"
            "1112" -> "Headset Audio Gateway"
            "111e" -> "Hands-Free"
            "111f" -> "Hands-Free Audio Gateway"
            "110a" -> "Audio Source"
            "110b" -> "Audio Sink"
            "110c" -> "A/V Remote Control Target"
            "110e" -> "A/V Remote Control"
            "110f" -> "A/V Remote Control Controller"
            "112f" -> "Phone Book Access Server"
            "1200" -> "PnP Information"
            else -> "Bluetooth-base UUID — not labeled here"
        }
    }
}
