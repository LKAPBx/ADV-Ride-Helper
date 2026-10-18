package dev.africatwin.telemetry

import android.os.*
import androidx.car.app.*
import androidx.car.app.model.*
import androidx.lifecycle.*

internal class TirePressureScreen(context: CarContext) : Screen(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable { override fun run() { invalidate(); handler.postDelayed(this, 1000) } }
    init { lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { handler.post(tick) }
        override fun onStop(owner: LifecycleOwner) { handler.removeCallbacks(tick) }
    }) }
    override fun onGetTemplate(): Template {
        if (!TpmsFeature.enabled(carContext)) return PaneTemplate.Builder(Pane.Builder()
            .addRow(Row.Builder().setTitle("TPMS disabled").addText("Enable TPMS in phone Settings to show tire data on both displays.").build()).build())
            .setTitle("Tire pressure").setHeaderAction(Action.BACK).build()
        val pane = Pane.Builder()
            .addRow(TireCarRow.build(carContext, "front"))
            .addRow(TireCarRow.build(carContext, "rear"))
            .addRow(Row.Builder().setTitle("Sensor monitoring").addText(TpmsMonitor.health(carContext))
                .addText("Readings show the last received broadcast.").build())
        if (carContext.carAppApiLevel >= 4) pane.setImage(TpmsGraphics.create(carContext))
        return PaneTemplate.Builder(pane.build()).setTitle("Tire pressure").setHeaderAction(Action.BACK).build()
    }
}
