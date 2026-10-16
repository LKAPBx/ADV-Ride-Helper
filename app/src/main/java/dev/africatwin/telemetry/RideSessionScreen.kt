package dev.africatwin.telemetry

import android.os.*
import androidx.car.app.*
import androidx.car.app.model.*
import androidx.lifecycle.*

internal class RideSessionScreen(context: CarContext) : Screen(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var last = ""
    private val tick = object : Runnable { override fun run() {
        val ride = RideStore(carContext)
        val key = "${ride.running}:${ride.sessionText()}:${ride.status}"
        if (key != last) { last = key; invalidate() }
        handler.postDelayed(this, 1000)
    } }
    init { lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { handler.post(tick) }
        override fun onStop(owner: LifecycleOwner) { handler.removeCallbacks(tick) }
    }) }
    override fun onGetTemplate(): Template {
        val ride = RideStore(carContext)
        val list = ItemList.Builder().addItem(Row.Builder().setTitle(if (ride.running) "Current tracking session" else "Last tracking session")
            .addText(ride.sessionText()).addText(ride.healthText()).build())
            .addItem(Row.Builder().setTitle("Session totals").addText("Elapsed time includes stops and waiting for GPS")
                .addText("Distance counts accepted GPS movement; resets on Start").build())
            .addItem(Row.Builder().setTitle("Completed ride summary").addText(ride.lastSummary).build())
        return ListTemplate.Builder().setTitle("Ride session").setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }
}
