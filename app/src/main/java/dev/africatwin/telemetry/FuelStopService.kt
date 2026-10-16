package dev.africatwin.telemetry

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.*
import androidx.car.app.*
import androidx.car.app.validation.HostValidator
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.*

class FuelStopService : CarAppService() {
    private val hondaSessions = mutableSetOf<LifecycleOwner>()
    override fun onDestroy() {
        if (hondaSessions.isNotEmpty()) RideDisconnect.handle(this, "Honda head unit")
        hondaSessions.clear()
        super.onDestroy()
    }
    override fun createHostValidator(): HostValidator =
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        else HostValidator.Builder(this)
            .addAllowedHost("com.google.android.projection.gearhead",
                "fdb00c43dbde8b51cb312aa81d3b5fa17713adb94b28f598d77f8eb89daceedf")
            .build()
    override fun onCreateSession(): Session = object : Session() {
        private var autoAttempted = false
        private var hostLocation: HostLocationBridge? = null
        init { lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                HeadUnitDiagnostics(carContext).capture(carContext, this@FuelStopService.hostInfo) { manufacturer, usable ->
                    when {
                        !HondaIdentity.matches(manufacturer, usable) -> "Automatic tracking: no successful Honda_Wing match"
                        !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) -> "Automatic tracking: session no longer active"
                        autoAttempted -> "Automatic tracking: already checked this session; manual Stop is respected"
                        else -> { hondaSessions.add(owner); autoAttempted = true; RideDisconnect.reconnected(carContext); HondaAutoTracking.attempt(carContext) }
                    }
                }
            }
            override fun onDestroy(owner: LifecycleOwner) {
                if (hondaSessions.remove(owner) && hondaSessions.isEmpty()) {
                    RideDisconnect.handle(this@FuelStopService, "Honda head unit")
                }
            }
        }) }
        override fun onCreateScreen(intent: Intent): Screen {
            if (hostLocation == null) hostLocation = HostLocationBridge(carContext, lifecycle)
            return FuelStopScreen(carContext)
        }
    }
}

class FuelStopScreen(context: CarContext, private val gaugesOnly: Boolean = false) : Screen(context) {
    private var mapFailed = false
    private var lastMapRefresh = 0L
    private var dashboardFailed = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastSummary = ""
    private val tick = object : Runnable {
        override fun run() {
            if (dashboardFailed) return
            val ride = RideStore(carContext)
            val estimate = FuelEstimateStore(carContext)
            val heading = LiveHeading.current()
            val map = !gaugesOnly && MapDashboard.enabled(carContext) && !mapFailed
            val summary = "${MapDashboard.enabled(carContext)}:${ride.running}:${ride.currentSessionText()}:${estimate.fuelText()}:${estimate.rangeText()}:${heading?.degrees?.toInt()}:${ride.healthText()}:${LiveSpeed.text(carContext)}:${TpmsStore(carContext).summary()}:${if (map) LivePosition.current() else null}"
            val now = SystemClock.elapsedRealtime()
            if (summary != lastSummary && now - lastMapRefresh >= 15_000) {
                lastSummary = summary
                lastMapRefresh = now
                invalidate()
            }
            handler.postDelayed(this, 1000)
        }
    }
    init { lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) { lastMapRefresh = SystemClock.elapsedRealtime(); invalidate(); handler.removeCallbacks(tick); handler.post(tick) }
        override fun onStop(owner: LifecycleOwner) { handler.removeCallbacks(tick) }
    }) }
    override fun onGetTemplate(): Template {
        if (dashboardFailed) return errorFallback()
        if (!gaugesOnly && MapDashboard.enabled(carContext) && !mapFailed) {
            try { return MapDashboard.build(this) }
            catch (error: RuntimeException) {
                mapFailed = true
                CrashReports.record(carContext, "Map dashboard; API ${carContext.carAppApiLevel}", error)
                CarToast.makeText(carContext, "Map dashboard unavailable; showing gauges", CarToast.LENGTH_LONG).show()
            }
        }
        return try { buildDashboard() } catch (error: RuntimeException) {
            dashboardFailed = true
            CrashReports.record(carContext, "Auto dashboard construction; API ${carContext.carAppApiLevel}", error)
            errorFallback()
        }
    }
    private fun errorFallback(): Template = PaneTemplate.Builder(Pane.Builder()
        .addRow(Row.Builder().setTitle("Dashboard could not load").addText("Open phone app and copy the saved error report").build())
        .addAction(RideControls.carAction(carContext) { invalidate() })
        .addAction(Action.Builder().setTitle("Find gas").setOnClickListener { screenManager.push(StationSearchScreen(carContext, searchOnOpen = true)) }.build()).build())
        .setTitle("Fuel & range").setHeaderAction(Action.APP_ICON).build()
    private fun buildDashboard(): Template {
        val estimate = FuelEstimateStore(carContext)
        val ride = RideStore(carContext)
        val heading = LiveHeading.current()
        val pane = Pane.Builder()
            .addRow(Row.Builder().setTitle("Fuel ${estimate.fuelText()} • Range ${estimate.rangeText()}")
                .addText("Session: ${ride.currentSessionText().replace(" elapsed", "")}")
                .addText(estimate.confidenceText()).build())
            .addRow(Row.Builder().setTitle("Tire pressure • ${AppUnits.get(carContext).pressureUnit} / ${AppUnits.get(carContext).temperatureUnit}")
                .setImage(TpmsGraphics.create(carContext, compact = true), Row.IMAGE_TYPE_SMALL)
                .addText("Front: ${TpmsStore(carContext).wheel("front").replace('\n', ' ')}")
                .addText("Rear: ${TpmsStore(carContext).wheel("rear").replace('\n', ' ')}")
                .build())
            .addRow(Row.Builder().setTitle(heading?.let { "${LiveHeading.cardinal(it.degrees)} ${it.degrees.toInt()}°" } ?: "Heading unavailable")
                .addText(ride.healthText()).addText("${LivePosition.status(carContext)} • ${LiveSpeed.text(carContext)}").build())
            .addAction(RideControls.carAction(carContext) { invalidate() })
            .addAction(Action.Builder().setTitle("Map view").setOnClickListener {
                MapDashboard.setEnabled(carContext, true)
                mapFailed = false
                if (gaugesOnly) screenManager.pop() else invalidate()
            }.build())
        if (carContext.carAppApiLevel >= 4) pane.setImage(DashboardPanel.create(estimate, heading))
        // The dedicated pane image is independent of row/grid icon slots.
        val actions = ActionStrip.Builder()
            .addAction(Action.Builder()
                .setIcon(CarIcon.Builder(IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_manage)).build())
                .setOnClickListener { screenManager.push(AutoOptionsScreen(carContext)) }.build())
            .addAction(Action.Builder().setTitle("Find gas")
                .setOnClickListener { screenManager.push(StationSearchScreen(carContext, searchOnOpen = true)) }.build())
            .build()
        return PaneTemplate.Builder(pane.build()).setTitle("Fuel & range").setHeaderAction(if (gaugesOnly) Action.BACK else Action.APP_ICON)
            .setActionStrip(actions).build()
    }
}

internal class AutoOptionsScreen(context: CarContext) : Screen(context) {
    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
            .addItem(Row.Builder().setTitle(if (MapDashboard.enabled(carContext)) "Dashboard: Map" else "Dashboard: Gauges")
                .addText("Switch the main head-unit display")
                .setOnClickListener {
                    MapDashboard.setEnabled(carContext, !MapDashboard.enabled(carContext))
                    screenManager.pop()
                }.build())
            .addItem(Row.Builder().setTitle("Session details").addText("Live distance and elapsed time")
                .setOnClickListener { screenManager.push(RideSessionScreen(carContext)) }.build())
            .addItem(Row.Builder().setTitle("Settings").addText("Configure tank size, permissions and appearance on phone").build())
            .addItem(Row.Builder().setTitle("Tire pressure").addText("Front/rear readings and parked refresh")
                .setOnClickListener { screenManager.push(TirePressureScreen(carContext)) }.build())
            .addItem(Row.Builder().setTitle("Advanced").addText("Diagnostics and test features")
                .setOnClickListener { screenManager.push(AutoAdvancedScreen(carContext)) }.build())
        return ListTemplate.Builder().setTitle("More & options").setHeaderAction(Action.BACK)
            .setSingleList(list.build()).build()
    }
}

internal class AutoAdvancedScreen(context: CarContext) : Screen(context) {
    override fun onGetTemplate(): Template {
        val items = ItemList.Builder()
            .addItem(Row.Builder().setTitle("GPS / compass test").addText("Compare Android Auto and phone GPS")
                .setOnClickListener { screenManager.push(HostSensorTestScreen(carContext)) }.build())
            .addItem(Row.Builder().setTitle("Test fuel notification").addText("Check how your head unit displays alerts")
                .setOnClickListener {
                    CarToast.makeText(carContext, if (FuelSummaryNotice.show(carContext)) "Notification sent" else "Check notification permission", CarToast.LENGTH_LONG).show()
                }.build())
            .addItem(Row.Builder().setTitle("Saved diagnostic reports").addText("Phone Settings → Advanced").build())
        return ListTemplate.Builder().setTitle("Advanced").setHeaderAction(Action.BACK)
            .setSingleList(items.build()).build()
    }
}


