package dev.africatwin.telemetry

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle

/** One-time, sequential setup. Never re-prompts a denied permission automatically. */
internal class FirstRunPermissions(private val activity: Activity, state: Bundle?, private val finished: () -> Unit) {
    private val prefs = activity.getSharedPreferences("permission_setup", Context.MODE_PRIVATE)
    private var waiting = state?.getBoolean("setupWaiting", false) ?: false
    private var dialogVisible = false
    init {
        // Older versions marked a skipped or partly granted walkthrough complete.
        // Offer one fresh review, preserving every actual Android permission.
        if (prefs.getInt("flowVersion", 0) < 2) {
            prefs.edit().putInt("flowVersion", 2).putBoolean("complete", false)
                .putBoolean("begun", false).putInt("step", 0).apply()
            waiting = false
        }
    }
    private val permissions get() = listOf(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        if (Build.VERSION.SDK_INT >= 29) arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else emptyArray(),
        if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN) else emptyArray(),
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray(),
        arrayOf(Manifest.permission.CAMERA))
    val complete get() = prefs.getBoolean("complete", false)
    fun save(state: Bundle) { state.putBoolean("setupWaiting", waiting) }
    fun retry() {
        if (waiting || dialogVisible) return
        prefs.edit().putBoolean("complete", false).putBoolean("begun", false).putInt("step", 0).apply()
        resume()
    }
    fun resume() {
        if (complete || waiting || dialogVisible || activity.isFinishing || activity.isDestroyed) return
        if (!prefs.getBoolean("begun", false)) {
            dialogVisible = true
            AlertDialog.Builder(activity).setTitle("Set up ADV Ride Helper permissions")
                .setMessage("We will request location, Bluetooth nearby-device access, notifications, and camera access for dashboard photos.\n\nADV Ride Helper uses location to record ride distance and automatically start Honda rides even when the app is closed or not in use. Location is stored on this phone; station searches send your search location to Overpass.\n\nFirst choose Precise and While using the app. The separate background-location step needs Allow all the time. You can decline any permission and enable it later in Settings.")
                .setPositiveButton("Set up permissions") { _, _ ->
                    dialogVisible = false; prefs.edit().putBoolean("begun", true).apply(); next()
                }.setNegativeButton("Not now") { _, _ -> skip() }
                .setOnCancelListener { skip() }.show()
        } else next()
    }
    private fun skip() {
        dialogVisible = false; prefs.edit().putBoolean("complete", true).apply(); finished()
    }
    private fun granted(permission: String) = activity.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    private fun next() {
        if (waiting || dialogVisible || complete || activity.isFinishing || activity.isDestroyed) return
        var step = prefs.getInt("step", 0)
        while (step < permissions.size) {
            val request = permissions[step]
            val needed = request.any { !granted(it) } &&
                (step != 1 || granted(Manifest.permission.ACCESS_FINE_LOCATION))
            prefs.edit().putInt("step", step + 1).apply()
            if (needed) {
                if (step == 1 && Build.VERSION.SDK_INT >= 30) {
                    prefs.edit().putInt("step", 1).apply()
                    dialogVisible = true
                    val option = activity.packageManager.backgroundPermissionOptionLabel
                    AlertDialog.Builder(activity).setTitle("Allow background location")
                        .setMessage("To start rides while ADV Ride Helper is closed, choose $option on the next screen. The earlier While using the app choice grants foreground access only. Keep precise location enabled. You can skip this and start tracking manually.")
                        .setPositiveButton("Continue") { _, _ ->
                            prefs.edit().putInt("step", 2).apply()
                            dialogVisible = false; waiting = true; activity.requestPermissions(request, 81)
                        }.setNegativeButton("Skip") { _, _ ->
                            prefs.edit().putInt("step", 2).apply(); dialogVisible = false; next()
                        }.setOnCancelListener {
                            prefs.edit().putInt("step", 2).apply(); dialogVisible = false; next()
                        }.show()
                } else {
                    waiting = true
                    activity.requestPermissions(request, 80 + step)
                }
                return
            }
            step++
        }
        prefs.edit().putBoolean("complete", true).apply()
        val missing = listOfNotNull(
            "Precise location".takeUnless { granted(Manifest.permission.ACCESS_FINE_LOCATION) },
            "Allow all the time (background location)".takeIf { Build.VERSION.SDK_INT >= 29 && !granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) },
            "Bluetooth nearby devices".takeIf { Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT) },
            "BLE sensor scanning".takeIf { Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_SCAN) },
            "Notifications".takeIf { Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS) },
            "Camera".takeUnless { granted(Manifest.permission.CAMERA) })
        if (missing.isNotEmpty()) AlertDialog.Builder(activity).setTitle("Some permissions are still off")
            .setMessage("${missing.joinToString("\n")}\n\nAndroid may stop showing prompts after repeated denials. You can grant access in Android app settings or run Set up permissions again in the app's Settings. For automatic starts, choose Permissions → Location → Allow all the time.")
            .setPositiveButton("Open app settings") { _, _ ->
                activity.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:${activity.packageName}")))
            }.setNegativeButton("Later", null).show()
        finished()
    }
    fun result(code: Int): Boolean {
        if (code !in 80..84) return false
        waiting = false
        activity.window.decorView.post { next() }
        return true
    }
}

