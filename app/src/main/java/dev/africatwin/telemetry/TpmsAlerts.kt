package dev.africatwin.telemetry

import android.app.*
import android.content.*
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.*
import android.widget.*

internal object TpmsAlerts {
    private val handler = Handler(Looper.getMainLooper())
    private val zeroRepeats = mutableMapOf<String, Runnable>()
    private fun scheduleZero(c: Context, id: String) {
        if (zeroRepeats.containsKey(id)) return
        val app = c.applicationContext
        val task = Runnable {
            zeroRepeats.remove(id)
            val store = TpmsStore(app)
            val reading = store.reading(id) ?: return@Runnable
            if (listOf("front", "rear").none { store.assigned(it) == id }) return@Runnable
            if (!store.stale(id) && TpmsAge.repeatZero(reading.first.kpa, System.currentTimeMillis() - reading.second))
                check(app, id, reading.first, repeatZero = true)
        }
        zeroRepeats[id] = task
        handler.postDelayed(task, 30_000)
    }
    fun assignmentsChanged(c: Context) {
        zeroRepeats.values.forEach(handler::removeCallbacks); zeroRepeats.clear()
        val edit = prefs(c).edit()
        prefs(c).all.keys.filter { it.startsWith("state:") }.forEach { edit.remove(it) }
        edit.apply()
        val manager = c.getSystemService(NotificationManager::class.java)
        manager.cancel(51); manager.cancel(52)
    }
    fun prefs(c: Context) = c.getSharedPreferences("tpms_alerts", Context.MODE_PRIVATE)
    fun backgroundEnabled(c: Context) = TpmsFeature.enabled(c) && prefs(c).getBoolean("background", true)
    fun threshold(c: Context, wheel: String) = prefs(c).getString(wheel, "0")?.toDoubleOrNull() ?: 0.0
    fun channels(c: Context) {
        val manager = c.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("tpms_low", "Low tire pressure", NotificationManager.IMPORTANCE_HIGH))
        manager.createNotificationChannel(NotificationChannel("tpms_zero", "Zero tire pressure warning", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).build())
            enableVibration(true)
        })
    }
    fun check(c: Context, id: String, value: FoboCandidate, repeatZero: Boolean = false) {
        if (!TpmsFeature.enabled(c)) return
        if (value.kpa != 0) zeroRepeats.remove(id)?.let(handler::removeCallbacks)
        val store = TpmsStore(c)
        listOf("front", "rear").filter { store.assigned(it) == id }.forEach { wheel ->
            val state = TpmsAge.warning(value.kpa, threshold(c, wheel))
            val key = "state:$wheel:$id"
            val old = prefs(c).getInt(key, 0)
            if (state == old && !(state == 2 && repeatZero)) {
                if (state == 2) scheduleZero(c, id)
                return@forEach
            }
            val manager = c.getSystemService(NotificationManager::class.java)
            val noticeId = if (wheel == "front") 51 else 52
            if (state == 0) { manager.cancel(noticeId); prefs(c).edit().putInt(key, state).apply(); return@forEach }
            channels(c)
            if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) return@forEach
            if (!manager.areNotificationsEnabled()) return@forEach
            val open = PendingIntent.getActivity(c, noticeId, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            manager.notify(noticeId, Notification.Builder(c, if (state == 2) "tpms_zero" else "tpms_low")
                .setSmallIcon(R.drawable.ic_app).setContentTitle(if (state == 2) "ZERO pressure reported — $wheel tire" else "Low pressure — $wheel tire")
                .setContentText(store.wheel(wheel).replace('\n', ' ')).setContentIntent(open)
                .setOnlyAlertOnce(false).setWhen(System.currentTimeMillis())
                .setColor(if (state == 2) Color.RED else Color.YELLOW).setAutoCancel(true).build())
            prefs(c).edit().putInt(key, state).apply()
            if (state == 2) scheduleZero(c, id)
        }
    }
    fun settings(activity: Activity) {
        val units = AppUnits.get(activity)
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 12, 24, 12) }
        val inputs = listOf("front", "rear").associateWith { wheel ->
            box.addView(TextView(activity).apply { text = "$wheel low-pressure threshold (${units.pressureUnit}; 0 disables low alert)" })
            EditText(activity).apply { inputType = 8194; setText(units.number(units.pressureValue(threshold(activity, wheel)), 3)); box.addView(this) }
        }
        box.addView(TextView(activity).apply { text = "Zero pressure triggers an alert, then repeats every 30 seconds while the latest reading is fresh and still zero. Repeats stop when pressure recovers or the reading reaches 5 minutes old. Sound depends on Android notification volume and Do Not Disturb settings." })
        box.addView(Button(activity).apply {
            text = "Choose zero-pressure warning sound"; PhoneButtons.style(this, false)
            setOnClickListener {
                channels(activity)
                activity.startActivity(Intent(android.provider.Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, activity.packageName)
                    .putExtra(android.provider.Settings.EXTRA_CHANNEL_ID, "tpms_zero"))
            }
        })
        val background = Switch(activity).apply { text = "Keep listening for tires without GPS"; isChecked = backgroundEnabled(activity) }
        box.addView(background)
        box.addView(TextView(activity).apply { text = "Continuously listens in low-power mode, even when GPS tracking is stopped. Readings become stale after 5 minutes without a new packet and recover when a fresh packet arrives." })
        val error = TextView(activity); box.addView(error)
        val dialog = AlertDialog.Builder(activity).setTitle("Tire pressure warnings").setView(box)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val values = inputs.mapValues { (wheel, input) ->
                    val original = threshold(activity, wheel)
                    if (input.text.toString() == units.number(units.pressureValue(original), 3)) original
                    else input.text.toString().toDoubleOrNull()?.let(units::pressureInput)
                }
                if (values.values.any { it == null || !it.isFinite() || it !in 0.0..150.0 }) { error.text = "Enter thresholds from 0 to ${units.pressure(150.0)}"; return@setOnClickListener }
                if (background.isChecked && !TpmsMonitor.allowed(activity)) { error.text = "Allow Nearby devices access in Android app settings before enabling monitoring."; return@setOnClickListener }
                if (background.isChecked && listOf("front", "rear").all { TpmsStore(activity).assigned(it).isEmpty() }) {
                    error.text = "Assign Front/Rear sensors in Advanced before enabling monitoring."; return@setOnClickListener
                }
                val edit = prefs(activity).edit()
                values.forEach { (wheel, value) -> edit.putString(wheel, value.toString()) }
                edit.putBoolean("background", background.isChecked).apply()
                if (background.isChecked) {
                    TpmsRecovery.arm(activity, true)
                    try { activity.startForegroundService(Intent(activity, TpmsBackgroundService::class.java)) }
                    catch (_: RuntimeException) { error.text = "Could not start tire monitoring; check Bluetooth permissions."; return@setOnClickListener }
                } else { TpmsRecovery.disarm(activity); activity.stopService(Intent(activity, TpmsBackgroundService::class.java)) }
                dialog.dismiss()
            }
        }
        dialog.show()
    }
}

class TpmsBackgroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() {
        if (!TpmsAlerts.backgroundEnabled(this@TpmsBackgroundService)) { stopSelf(); return }
        TpmsMonitor.start(this@TpmsBackgroundService, "background")
        TpmsRecovery.arm(this@TpmsBackgroundService)
        TpmsMonitor.retainBackgroundStaleness(this@TpmsBackgroundService)
        // Health check only: start() leaves the existing BLE scan running.
        handler.postDelayed(this, 5_000)
    } }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, id: Int): Int {
        if (intent?.action == "STOP") { TpmsAlerts.prefs(this).edit().putBoolean("background", false).apply(); TpmsRecovery.disarm(this); stopSelf(); return START_NOT_STICKY }
        if (!TpmsAlerts.backgroundEnabled(this) || !TpmsMonitor.allowed(this) ||
            listOf("front", "rear").all { TpmsStore(this).assigned(it).isEmpty() }) {
            TpmsRecovery.disarm(this); stopSelf(); return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("tpms_monitor", "Tire monitoring", NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 53, Intent(this, TpmsBackgroundService::class.java).setAction("STOP"), PendingIntent.FLAG_IMMUTABLE)
            val notice = Notification.Builder(this, "tpms_monitor").setSmallIcon(R.drawable.ic_app).setContentTitle("Tire monitoring active")
                .setContentText("Continuously listening for tire sensors • low-power BLE • GPS not required")
                .addAction(Notification.Action.Builder(null, "Stop monitoring", stop).build()).setOngoing(true).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(53, notice, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE) else startForeground(53, notice)
            handler.removeCallbacks(tick); handler.post(tick)
        } catch (error: RuntimeException) {
            getSharedPreferences("tire_diagnostics", 0).edit().putString("lastIssue", "Foreground monitoring blocked (${error.javaClass.simpleName})").apply()
            stopSelf(); return START_NOT_STICKY
        }
        return START_STICKY
    }
    override fun onDestroy() { handler.removeCallbacks(tick); TpmsMonitor.stop("background"); super.onDestroy() }
}
