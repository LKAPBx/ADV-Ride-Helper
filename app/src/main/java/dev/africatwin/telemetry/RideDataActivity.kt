package dev.africatwin.telemetry

import android.app.*
import android.content.*
import android.graphics.Color
import android.os.*
import android.widget.*
import android.view.WindowInsets
import java.io.ByteArrayOutputStream

class RideDataActivity : Activity() {
    private var dark = false
    private val ink get() = if (dark) Color.WHITE else Color.BLACK
    private fun button(text: String, action: () -> Unit) = Button(this).apply {
        this.text = text; isAllCaps = false; PhoneButtons.style(this, dark); setOnClickListener { action() }
    }
    private fun show(title: String, text: String) {
        val view = TextView(this).apply { this.text = text; textSize = 15f; setTextColor(ink); setPadding(24, 16, 24, 16); setTextIsSelectable(true) }
        AlertDialog.Builder(this).setTitle(title).setView(ScrollView(this).apply { addView(view) })
            .setPositiveButton("Close", null).setNeutralButton("Copy") { _, _ ->
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(title, text))
            }.show()
    }
    override fun onCreate(state: Bundle?) {
        val mode = getSharedPreferences("appearance", 0).getString("mode", "system")
        dark = mode == "dark" || (mode == "system" && resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK == android.content.res.Configuration.UI_MODE_NIGHT_YES)
        setTheme(if (dark) android.R.style.Theme_Material_NoActionBar else android.R.style.Theme_Material_Light_NoActionBar)
        super.onCreate(state)
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 16, 24, 16); setBackgroundColor(if (dark) Color.rgb(16,24,21) else Color.WHITE) }
        box.addView(TextView(this).apply { text = "History, backup & monitoring"; textSize = 23f; setTextColor(ink) })
        box.addView(button("Ride & fill-up history") { show("History", RideHistory(this).text()) })
        box.addView(button("Export history CSV") { createDocument(301, "text/csv", "ADV-Ride-Helper-history.csv") })
        box.addView(button("Export backup") { createDocument(300, "application/json", "ADV-Ride-Helper-backup.json") })
        box.addView(button("Restore backup") {
            if (RideStore(this).requestedRunning) { show("Stop tracking first", "Stop GPS tracking before restoring a backup."); return@button }
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE), 302)
        })
        box.addView(TextView(this).apply { text = "Backup includes settings, sensor assignments, fuel baseline, OCR boxes and up to 1,000 history entries. It excludes live readings, photos, locations and Android permissions. Choose where to save it. CSV measurements use meters, US gallons, US MPG and elapsed milliseconds; dates use Unix milliseconds."; setTextColor(ink); setPadding(0, 16, 0, 16) })
        box.addView(button("Tire monitoring report") { show("Tire monitoring", TireDiagnostics.report(this)) })
        box.addView(button("Reset sensor timing statistics") { TireDiagnostics.reset(this); show("Timing statistics reset", "New packets will build a fresh interval sample.") })
        box.addView(button("Restart tire monitoring") {
            TpmsMonitor.reset(this, "Restart requested")
            TpmsRecovery.arm(this, true)
            if (TpmsAlerts.backgroundEnabled(this) && TpmsMonitor.allowed(this)) runCatching { startForegroundService(Intent(this, TpmsBackgroundService::class.java)) }
            show("Monitoring status", TpmsMonitor.health(this))
        })
        box.addView(button("Start phone battery observation") { PhonePower.begin(this); show("Observation started", "Leave GPS stopped and use the phone normally or leave it idle. Finish after a useful interval, such as an hour. Repeat under similar conditions with tire monitoring off for comparison.") })
        box.addView(button("Finish phone battery observation") {
            runCatching { PhonePower.finish(this) }.onFailure { show("Observation unavailable", it.message.orEmpty()); return@button }
            show("Phone battery observation", PhonePower.report(this))
        })
        box.addView(button("Close") { finish() })
        val scroll = ScrollView(this).apply { addView(box) }
        scroll.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val safe = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                view.setPadding(safe.left, safe.top, safe.right, safe.bottom)
            } else view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(scroll)
    }
    private fun createDocument(code: Int, mime: String, name: String) {
        startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).setType(mime).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE, name), code)
    }
    @Deprecated("Platform callback")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || requestCode !in 300..302) return
        val uri = data?.data ?: return
        runCatching {
            if (requestCode == 302) {
                val bytes = contentResolver.openInputStream(uri)?.use { input ->
                    val out = ByteArrayOutputStream(); val buffer = ByteArray(4096)
                    while (true) { val n = input.read(buffer); if (n < 0) break; require(out.size() + n <= 2_000_000) { "Backup too large" }; out.write(buffer, 0, n) }
                    out.toByteArray()
                } ?: error("Could not open backup")
                val root = RideBackup.parse(String(bytes, Charsets.UTF_8))
                AlertDialog.Builder(this).setTitle("Restore backup?").setMessage(RideBackup.preview(root)).setNegativeButton("Cancel", null)
                    .setPositiveButton("Restore") { _, _ ->
                        runCatching {
                            RideBackup.restore(this, root)
                            if (TpmsAlerts.backgroundEnabled(this) && TpmsMonitor.allowed(this)) startForegroundService(Intent(this, TpmsBackgroundService::class.java))
                        }.onSuccess { show("Backup restored", "Saved data restored. Android permissions stay unchanged. Sensor readings will update when new packets arrive. Restart the app to apply appearance changes.") }
                            .onFailure { show("Restore failed", it.message ?: "Could not restore backup") }
                    }.show()
            } else {
                val text = if (requestCode == 300) RideBackup.encode(this) else RideHistory(this).csv()
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } ?: error("Could not write file")
                show("Export saved", if (requestCode == 300) "Backup saved to your selected location." else "History CSV saved to your selected location.")
            }
        }.onFailure { show("File operation failed", it.message ?: "Could not use this file") }
    }
}
