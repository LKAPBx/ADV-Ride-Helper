package dev.africatwin.telemetry

import android.Manifest
import android.content.pm.PackageManager
import android.location.*
import android.os.*
import android.content.Context
import androidx.lifecycle.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.concurrent.Executors

internal class FuelSearchController(private val context: Context, lifecycle: Lifecycle?,
    private val autoRefresh: Boolean = true, private val changed: () -> Unit) : DefaultLifecycleObserver {
    var message = "Allow precise location in the phone app to search"
        private set
    var stations: List<FoundStation> = emptyList()
        private set
    var attribution = ""
        private set
    val freshness get() = StationResultsStore(context).freshness()
    private val handler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val locations = context.getSystemService(LocationManager::class.java)
    @Volatile private var started = false
    private var searching = false
    private var searchWhenReady = false
    private var observedOtherSearch = false
    private var querying = false
    @Volatile private var generation = 0
    private var currentTask: java.util.concurrent.Future<*>? = null
    companion object {
        // Controllers run on the main thread; share ownership across phone and car screens.
        private var activeSearch: FuelSearchController? = null
        @Volatile private var lastRequest = 0L
        private var lastAutomaticAttempt = 0L
        fun autoThreshold(context: Context) = context.getSharedPreferences("station_search_options", Context.MODE_PRIVATE)
            .getInt("autoPercent", 10).takeIf { it in 1..100 } ?: 10
    }
    val cooldownSeconds get() = if (lastRequest == 0L) 0 else
        ((60_000L - (SystemClock.elapsedRealtime() - lastRequest)).coerceAtLeast(0) + 999) / 1000
    val canSearch get() = activeSearch == null && !searching && cooldownSeconds == 0L
    val buttonText get() = when {
        searching || activeSearch != null -> "Searching…"
        cooldownSeconds > 0 -> "Find gas (${cooldownSeconds}s)"
        else -> "Find gas"
    }
    private var lastButtonState = ""
    private val cooldownTick = object : Runnable {
        override fun run() {
            if (!started) return
            if (activeSearch != null && activeSearch !== this@FuelSearchController) {
                observedOtherSearch = true
                searchWhenReady = false
            }
            if (observedOtherSearch && activeSearch == null) {
                observedOtherSearch = false
                stations = StationResultsStore(context).recent()
                if (stations.isNotEmpty()) attribution = "© OpenStreetMap contributors"
                message = "Search finished • ${StationResultsStore(context).freshness()}"
                changed()
            }
            if (searchWhenReady && canSearch) { searchWhenReady = false; search() }
            val state = "${AppUnits.get(context)}:$canSearch:$buttonText:${stations.joinToString { StationDirections.describe(context, it) }}"
            if (state != lastButtonState) { lastButtonState = state; changed() }
            handler.postDelayed(this, 1000)
        }
    }
    private val refresh = Runnable {
        if (started && autoRefresh) {
            val estimate = FuelEstimateStore(context)
            val now = SystemClock.elapsedRealtime()
            val eligible = RideSafety.stationSearchDue(estimate.remaining, estimate.filledCapacity, autoThreshold(context))
            if (eligible && !searching && (lastAutomaticAttempt == 0L || now - lastAutomaticAttempt >= 300_000L) &&
                (lastRequest == 0L || now - lastRequest >= 300_000L)) {
                lastAutomaticAttempt = now
                search()
            }
            schedule()
        }
    }
    private val timeout: Runnable = Runnable {
        locations.removeUpdates(listener)
        if (started && searching && !querying) {
            val (location, label) = LocationFallback.choose(context, locations)
            query(location, label)
        }
    }
    private val listener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!started || !searching || querying) return
            val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            if (age !in 0L..60_000_000_000L || !location.hasAccuracy() || location.accuracy > 200f) return
            LocationFallback.remember(context, location)
            locations.removeUpdates(this); handler.removeCallbacks(timeout); query(location)
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }
    init { lifecycle?.addObserver(this) }
    override fun onStart(owner: LifecycleOwner) { start() }
    fun start() {
        if (started) return
        started = true
        handler.post(cooldownTick)
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            stations = StationResultsStore(context).recent()
            if (stations.isNotEmpty()) { attribution = "© OpenStreetMap contributors"; message = "Saved search results • Distance from search location"; changed() }
            else { message = "Tap Search for nearby gas stations"; changed() }
        } else { message = "Allow precise location in the phone app"; changed() }
        if (autoRefresh) handler.postDelayed(refresh, 1500)
    }
    override fun onStop(owner: LifecycleOwner) { stop() }
    fun stop() {
        if (activeSearch === this) activeSearch = null
        searchWhenReady = false
        started = false; searching = false; querying = false; generation++
        currentTask?.cancel(true); currentTask = null
        handler.removeCallbacks(cooldownTick)
        handler.removeCallbacks(refresh); handler.removeCallbacks(timeout); locations.removeUpdates(listener)
        stations = emptyList(); attribution = ""
    }
    override fun onDestroy(owner: LifecycleOwner) { close() }
    fun close() { stop(); executor.shutdownNow() }
    private fun schedule() { handler.removeCallbacks(refresh); if (started && autoRefresh) handler.postDelayed(refresh, 10_000) }
    fun searchOnOpen() {
        if (activeSearch != null) {
            observedOtherSearch = activeSearch !== this
            message = "Station search already in progress"
            changed()
            return
        }
        // Keep the existing service cooldown, but require no second click when it ends.
        searchWhenReady = cooldownSeconds > 0
        if (searchWhenReady) { message = "Search will start when the countdown ends"; changed() }
        else search()
    }
    fun search() {
        if (!started || searching || activeSearch != null) return
        handler.removeCallbacks(refresh)
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            message = "Allow precise location in the phone app"; changed(); schedule(); return
        }
        if (cooldownSeconds > 0) {
            changed(); schedule(); return
        }
        searching = true
        activeSearch = this
        message = "Finding current location…"; changed()
        LivePosition.current()?.let {
            LocationFallback.remember(context, it); query(it); return
        }
        try {
            val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { locations.isProviderEnabled(it) }
            val fresh = providers.mapNotNull { locations.getLastKnownLocation(it) }.filter {
                NearestStations.validCoordinates(it.latitude, it.longitude) && it.hasAccuracy() && it.accuracy in 0f..200f &&
                    SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos in 0L..60_000_000_000L
            }.maxByOrNull { it.time }
            if (fresh != null) { LocationFallback.remember(context, fresh); query(fresh); return }
            if (providers.isEmpty()) { val (location, label) = LocationFallback.choose(context, locations); query(location, label); return }
            providers.forEach { locations.requestLocationUpdates(it, 1000L, 0f, listener, Looper.getMainLooper()) }
            handler.postDelayed(timeout, 20_000)
        } catch (e: RuntimeException) {
            locations.removeUpdates(listener)
            if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                val (location, label) = LocationFallback.choose(context, locations); query(location, label)
            } else { searching = false; activeSearch = null; message = "Allow precise location in the phone app"; changed(); schedule() }
        }
    }
    private fun query(location: Location, origin: String = "Current location") {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            searching = false; activeSearch = null; message = "Allow precise location in the phone app"; changed(); schedule(); return
        }
        querying = true
        message = "Finding gas stations within ${radiusText(NearestStations.INITIAL_SEARCH_RADIUS)} • $origin"; changed()
        lastRequest = SystemClock.elapsedRealtime()
        val requestGeneration = generation
        currentTask = executor.submit {
            val result = runCatching {
                var retries = 0
                var radius = NearestStations.INITIAL_SEARCH_RADIUS
                var found: List<FoundStation>
                while (true) {
                    if (!started || generation != requestGeneration || Thread.currentThread().isInterrupted)
                        throw java.util.concurrent.CancellationException()
                    try {
                        lastRequest = SystemClock.elapsedRealtime()
                        found = lookup(location, radius, retries + 1).map { it.copy(searchSource = origin) }
                        val nextRadius = NearestStations.nextSearchRadius(found.size, radius)
                        if (nextRadius != null) {
                            val available = found
                            handler.post {
                                if (started && generation == requestGeneration &&
                                    context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                    // Retain useful results if the wider request later fails.
                                    if (available.isNotEmpty()) {
                                        stations = available
                                        StationResultsStore(context).save(available)
                                        attribution = "© OpenStreetMap contributors"
                                    }
                                    message = "Found ${available.size} of 3 stations • expanding to ${radiusText(nextRadius)}"
                                    changed()
                                }
                            }
                            radius = nextRadius
                            continue
                        }
                        break
                    } catch (error: java.io.IOException) {
                        if (!StationFailure.shouldRetry(error, retries)) throw error
                        retries++
                        val waitMillis = StationFailure.retryDelayMillis(error, retries)
                        val retry = retries
                        handler.post {
                            if (started && generation == requestGeneration) {
                                message = "Search delayed • retry $retry/5 in ${(waitMillis + 999) / 1000}s"
                                changed()
                            }
                        }
                        Thread.sleep(waitMillis)
                        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                            throw SecurityException()
                        handler.post {
                            if (started && generation == requestGeneration) { message = "Retrying station search ($retry/5)…"; changed() }
                        }
                    }
                }
                found to radius
            }
            handler.post {
                if (!started || generation != requestGeneration) return@post
                searching = false; querying = false
                if (activeSearch === this) activeSearch = null
                if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    message = "Allow precise location in the phone app"
                } else result.onSuccess { (found, radius) ->
                    val age = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
                    // Results remain tied to the original search position during bounded retries.
                    if (age < 0L) {
                        message = "Location expired; retry • saved results retained"
                    } else {
                        stations = found
                        StationResultsStore(context).save(found)
                        attribution = "© OpenStreetMap contributors"
                        message = (if (found.isEmpty()) "No mapped station within ${radiusText(radius)}"
                            else if (found.size < 3) "Found ${found.size} mapped stations within ${radiusText(radius)} • Straight-line distance"
                            else "Nearest mapped stations • Straight-line distance") + " • $origin"
                    }
                }.onFailure { error -> message = StationFailure.describe(error) + if (stations.isEmpty()) "" else " • Showing saved results" }
                changed(); schedule()
            }
        }
    }
    private fun radiusText(meters: Double) = AppUnits.get(context).distance(meters / 1609.344, 0)
    private fun lookup(location: Location, radius: Double, attempt: Int): List<FoundStation> {
        val coords = String.format(Locale.US, "%.6f,%.6f", location.latitude, location.longitude)
        // Bound optional store-name enrichment to the local area; never scan shops across 60 miles.
        val includeStores = attempt == 1 && radius == NearestStations.INITIAL_SEARCH_RADIUS
        val storesQuery = if (includeStores) "nwr[\"shop\"=\"convenience\"](around:5000,$coords);" else ""
        val query = "[out:json][timeout:20];(nwr[\"amenity\"=\"fuel\"](around:${radius.toInt()},$coords);$storesQuery);out center tags;"
        val connection = URL("https://overpass-api.de/api/interpreter").openConnection() as HttpURLConnection
        val began = SystemClock.elapsedRealtime()
        var status = "Not received"
        var outcome = "Request did not complete"
        var bytesRead = 0
        try {
            // Allow for the public server's admission queue in addition to query execution.
            connection.requestMethod = "POST"; connection.connectTimeout = 15_000; connection.readTimeout = 45_000
            connection.instanceFollowRedirects = false; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
            connection.setRequestProperty("User-Agent", "ADVRideHelper/$version (Android fuel station search)")
            connection.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode; status = code.toString()
            if (code != 200) {
                val retryAfter = connection.getHeaderField("Retry-After")
                val delay = retryAfter?.toLongOrNull()?.takeIf { it >= 0 && it <= Long.MAX_VALUE / 1000 }?.times(1000)
                    ?: runCatching { java.time.ZonedDateTime.parse(retryAfter, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                        .toInstant().toEpochMilli() - System.currentTimeMillis() }.getOrDefault(0L).coerceAtLeast(0)
                throw StationResponseFailure(StationFailure.http(code), code in listOf(429, 502, 503, 504),
                    maxOf(delay, if (code == 429) 30_000L else 0L))
            }
            val raw = connection.inputStream.use { input ->
                val output = ByteArrayOutputStream(); val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    if (output.size() + count > 2_000_000) throw StationResponseFailure("Station response too large; try a smaller area later")
                    output.write(buffer, 0, count); bytesRead += count
                }
                output.toByteArray()
            }
            val json = JSONObject(String(raw, Charsets.UTF_8))
            if (json.has("remark")) {
                val remark = json.optString("remark").lowercase(Locale.US)
                throw StationResponseFailure("Station service could not finish the search; partial results rejected",
                    remark.contains("timed out") || remark.contains("timeout"))
            }
            val elements = json.getJSONArray("elements")
            fun tagMap(tags: JSONObject): Map<String, String> = tags.keys().asSequence().associateWith { tags.optString(it) }
            val nearbyStores = (0 until elements.length()).mapNotNull { i ->
                val e = elements.getJSONObject(i); val tags = e.optJSONObject("tags") ?: return@mapNotNull null
                if (tags.optString("shop") != "convenience") return@mapNotNull null
                val name = StationNames.choose(tagMap(tags)) ?: return@mapNotNull null
                val center = e.optJSONObject("center") ?: e
                val lat = center.optDouble("lat", Double.NaN); val lon = center.optDouble("lon", Double.NaN)
                if (!NearestStations.validCoordinates(lat, lon)) return@mapNotNull null
                StationPoint("store/$i", name, lat, lon)
            }
            val points = (0 until elements.length()).mapNotNull { i ->
                val e = elements.getJSONObject(i); val tags = e.optJSONObject("tags") ?: return@mapNotNull null
                if (tags.optString("amenity") != "fuel" || tags.optString("access") in listOf("private", "no") || tags.optString("fuel:gasoline") == "no") return@mapNotNull null
                val center = e.optJSONObject("center") ?: e
                val lat = center.optDouble("lat", Double.NaN); val lon = center.optDouble("lon", Double.NaN)
                if (!NearestStations.validCoordinates(lat, lon)) return@mapNotNull null
                val stores = nearbyStores.map { it to NearestStations.distance(lat, lon, it.latitude, it.longitude) }
                    .filter { it.second <= 50.0 }.sortedBy { it.second }.distinctBy { it.first.name }
                val storeName = stores.firstOrNull()?.takeIf { stores.size == 1 || stores[1].second - it.second >= 10.0 }?.first?.name
                val address = listOf(tags.optString("addr:housenumber"), tags.optString("addr:street")).filter { it.isNotBlank() }.joinToString(" ")
                val name = StationNames.choose(tagMap(tags)) ?: storeName?.let { "Fuel near $it" }
                    ?: if (address.isNotBlank()) "Gas station at $address" else "Unnamed gas station"
                StationPoint("${e.optString("type")}/${e.optLong("id")}", name, lat, lon)
            }
            val selected = NearestStations.select(points, location.latitude, location.longitude, radius)
            outcome = "Success: ${selected.size} stations"
            return selected
        } catch (error: Exception) {
            outcome = "${StationFailure.describe(error)}\nFailure type: ${error.javaClass.simpleName}"
            throw error
        } finally {
            val report = "${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}\n" +
                "Service: overpass-api.de\nAttempt: $attempt (maximum 5 retries)\nHTTP: $status\nRadius: ${radiusText(radius)}\n" +
                "Duration: ${SystemClock.elapsedRealtime() - began} ms\nReceived: $bytesRead bytes\nOptional store names: $includeStores\n$outcome\n" +
                "Coordinates and response contents are not included."
            val diagnostics = context.getSharedPreferences("station_diagnostics", Context.MODE_PRIVATE)
            val previous = diagnostics.getString("history", "").orEmpty().split("\n--- attempt ---\n").filter { it.isNotBlank() }.takeLast(5)
            val history = (previous + report).joinToString("\n--- attempt ---\n")
            diagnostics.edit().putString("history", history).putString("report", history).apply()
            connection.disconnect()
        }
    }
}

