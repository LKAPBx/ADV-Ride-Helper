package dev.africatwin.telemetry

import android.os.*
import androidx.car.app.*
import androidx.car.app.model.*
import androidx.lifecycle.*

internal class TirePressureScreen(context: CarContext) : Screen(context) {
    private val handler = Handler(Looper.getMainLooper())
    private val owner = "carTires:${System.identityHashCode(this)}"
    private var message = "Refresh while parked. Readings show the last received broadcast."
    private val tick = object : Runnable { override fun run() { invalidate(); handler.postDelayed(this, 1000) } }
    init { lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { handler.post(tick) }
        override fun onStop(owner: LifecycleOwner) { handler.removeCallbacks(tick); TpmsMonitor.stop(this@TirePressureScreen.owner) }
    }) }
    override fun onGetTemplate(): Template {
        val store = TpmsStore(carContext)
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle("Front tire").addText(store.wheel("front")).addText("Battery: ${store.battery("front")}").build())
            .addRow(Row.Builder().setTitle("Rear tire").addText(store.wheel("rear")).addText("Battery: ${store.battery("rear")}").build())
            .addRow(Row.Builder().setTitle(TpmsMonitor.health(carContext)).addText(message).addText("Assign sensors in phone Settings → Advanced → Scan TPMS sensors.").build())
            .addAction(Action.Builder().setTitle("Refresh tires")
                .setOnClickListener(ParkedOnlyOnClickListener.create {
                    message = TpmsMonitor.refresh(carContext, owner); invalidate()
                }).build())
        if (carContext.carAppApiLevel >= 4) pane.setImage(TpmsGraphics.create(carContext))
        return PaneTemplate.Builder(pane.build()).setTitle("Tire pressure").setHeaderAction(Action.BACK).build()
    }
}
