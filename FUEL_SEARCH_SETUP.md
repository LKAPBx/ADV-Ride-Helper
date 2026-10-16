> Historical guide: some controls and version-specific steps below have changed. See [the current README](README.md) for version 0.69 behavior and installation.

# Nearest gas station search — no billing setup

The Google price/routing service has been removed. The app now queries the public Overpass API directly for OpenStreetMap fuel stations. No Google API key, billing account, server, port forwarding or manual range entry is needed.

## Next steps

1. Rebuild and Run the app in Android Studio. Existing APKs under `app/build` predate this change.
2. Open the phone app and turn on **Enable automatic nearby station search**.
3. Tap **Allow location for station search** and grant precise location. Enable phone location and internet access.
4. Open the app in Android Auto/DHU. It searches automatically while the car screen is open. Tap **Find nearest gas station** to refresh, then tap a station to navigate through an installed navigation app.

No station details need to be entered. The optional saved stop and debug demo entry still exist as fallbacks, separate from real search results. Android Auto/DHU setup is in README.

## Search behavior and limits

- Queries mapped `amenity=fuel` nodes, ways and relations within 5 km. If no eligible result is found, tries 25 km.
- Uses station/area-center coordinates to calculate straight-line distance and returns the nearest three mapped candidates. Area centers can differ from station entrances.
- Excludes explicitly private/no-access entries and entries explicitly tagged as not selling gasoline. Unknown tags remain unknown; regular-grade availability and hours are not verified.
- No road routing, fuel-price comparison, range filter or promise of reachability. The navigation app handles the actual route after you select a station. Honda range probing remains debug logging only.
- Requires a precise phone fix, no older than 60 seconds and within 200 meters of reported accuracy. No background-location permission.
- Automatic refresh every five minutes while the screen is started; manual refresh at most once per minute. Stops location listeners when the screen stops. Does not rapidly retry service errors.
- Requests are HTTPS and bounded in size and time. A disabled search, denied location, no mapped stations, partial provider response or service error produces an explicit state.
- Public Overpass service has usage limits and no guaranteed availability. Suitable for personal prototype testing; review hosting capacity/policy before distributing to many users. Internet/data charges from your mobile carrier may still apply.

Enabling the switch sends rounded coordinates to the public Overpass service. No API key or range is transmitted. Results remain in memory. The app displays **© OpenStreetMap contributors** and the license link; OpenStreetMap coverage is community-maintained and may omit or contain outdated stations.

## Validation

Debug and release source compilation checked against cached dependencies and the installed SDK. Executable nearest-station checks cover sorting, duplicate IDs, invalid coordinates, radius exclusion, empty results, known distance and crossing the international date line. Full Gradle build/lint and phone/Android Auto runtime testing still need Android Studio and a connected phone. Live lookup behavior has not been validated from your location.

## Sources

- [Overpass public instances and usage policy](https://wiki.openstreetmap.org/wiki/Overpass_API)
- [OpenStreetMap attribution and license](https://www.openstreetmap.org/copyright)

