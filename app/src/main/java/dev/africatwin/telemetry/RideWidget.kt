package dev.africatwin.telemetry

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews

/** Standard Android widget; car placement depends on the host's widget support. */
class RideWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { render(context, manager, ids) }
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) { render(context, manager, intArrayOf(id)) }
    companion object {
        private val handler = Handler(Looper.getMainLooper())
        private var pending = false
        // Coalesce preference bursts; do not start GPS/BLE or a polling service for a widget.
        fun changed(context: Context) {
            if (pending) return
            pending = true
            val app = context.applicationContext
            handler.postDelayed({
                pending = false
                runCatching {
                    val manager = AppWidgetManager.getInstance(app)
                    render(app, manager, manager.getAppWidgetIds(ComponentName(app, RideWidget::class.java)))
                }
            }, 1000)
        }
        private fun render(context: Context, manager: AppWidgetManager, ids: IntArray) {
            if (ids.isEmpty()) return
            val fuel = FuelEstimateStore(context)
            val ride = RideStore(context)
            val tires = TpmsStore(context)
            val front = "Front: ${tires.wheel("front").replace('\n', ' ')}"
            val rear = "Rear: ${tires.wheel("rear").replace('\n', ' ')}"
            val views = RemoteViews(context.packageName, R.layout.ride_widget)
            views.setTextViewText(R.id.widget_status, "ADV Ride Helper • ${if (ride.running) "Tracking" else "Stopped"}")
            views.setTextViewText(R.id.widget_fuel, "${fuel.fuelText()} • ${fuel.rangeText()}\nFuel / range estimate")
            val tireVisibility = if (TpmsFeature.enabled(context)) android.view.View.VISIBLE else android.view.View.GONE
            views.setViewVisibility(R.id.widget_front, tireVisibility)
            views.setViewVisibility(R.id.widget_rear, tireVisibility)
            views.setTextViewText(R.id.widget_front, front)
            views.setTextViewText(R.id.widget_rear, rear)
            val time = android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date())
            views.setTextViewText(R.id.widget_updated, "Snapshot $time • updates with ride/sensor data")
            val color = context.getColor(R.color.widget_text)
            fun tireColor(wheel: String) = if (tires.stale(tires.assigned(wheel))) color else when (tires.warning(wheel)) {
                2 -> android.graphics.Color.rgb(210, 45, 45)
                1 -> android.graphics.Color.rgb(175, 115, 0)
                else -> color
            }
            views.setTextColor(R.id.widget_front, tireColor("front"))
            views.setTextColor(R.id.widget_rear, tireColor("rear"))
            val open = PendingIntent.getActivity(context, 118, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, open)
            manager.updateAppWidget(ids, views)
        }
    }
}

internal object WidgetUpdates {
    private val observed = mutableListOf<android.content.SharedPreferences>()
    private val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> app?.let { RideWidget.changed(it) } }
    private var app: Context? = null
    fun start(context: Context) {
        if (app != null) return
        app = context.applicationContext
        for (prefs in listOf(FuelRideState.preferences(context)) + listOf("tpms", "tpms_alerts", "units").map { context.getSharedPreferences(it, Context.MODE_PRIVATE) }) {
            prefs.also { observed.add(it); it.registerOnSharedPreferenceChangeListener(listener) }
        }
        RideWidget.changed(context)
    }
}
