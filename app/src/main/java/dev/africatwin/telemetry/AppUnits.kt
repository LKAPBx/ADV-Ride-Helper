package dev.africatwin.telemetry

import android.content.Context
import android.app.Activity
import android.app.AlertDialog
import android.widget.*

internal object AppUnits {
    fun valid(key: String, value: String) = value in choices[key].orEmpty()
    private val choices = UnitChoices.values
    fun photoEconomy(context: Context) = context.getSharedPreferences("units", Context.MODE_PRIVATE)
        .getString("Photo MPG convention", "US MPG")?.takeIf { it == "Imp MPG" } ?: "US MPG"
    fun get(context: Context): UnitFormats {
        val prefs = context.getSharedPreferences("units", Context.MODE_PRIVATE)
        fun selected(key: String) = prefs.getString(key, null)?.takeIf { it in choices.getValue(key) } ?: choices.getValue(key).first()
        return UnitFormats(selected("Distance"), selected("Speed"), selected("Fuel volume"), selected("Fuel economy"), selected("Tire pressure"), selected("Temperature"))
    }
    fun settings(activity: Activity, saved: () -> Unit) {
        val prefs = activity.getSharedPreferences("units", Context.MODE_PRIVATE)
        val box = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 16, 28, 16) }
        val inputs = choices.mapValues { (key, values) ->
            box.addView(TextView(activity).apply { text = key })
            Spinner(activity).apply {
                adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_dropdown_item, values)
                setSelection(values.indexOf(prefs.getString(key, values.first())).coerceAtLeast(0))
                box.addView(this)
            }
        }
        box.addView(TextView(activity).apply { text = "Choose each unit independently. Saved readings and warning levels keep the same physical values. GPS accuracy uses feet with miles, or meters with kilometers. Time remains hours:minutes:seconds; heading remains degrees. Set Photo MPG convention to match your dashboard; explicit km, L/100 km and km/L photo labels are converted automatically. Check every suggested value before saving." })
        AlertDialog.Builder(activity).setTitle("Units").setView(ScrollView(activity).apply { addView(box) })
            .setNegativeButton("Cancel", null).setPositiveButton("Save") { _, _ ->
                val edit = prefs.edit()
                inputs.forEach { (key, spinner) -> edit.putString(key, choices.getValue(key)[spinner.selectedItemPosition]) }
                edit.apply(); saved()
            }.show()
    }
}
