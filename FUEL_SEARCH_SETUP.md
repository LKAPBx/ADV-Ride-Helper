# Nearest gas station search

The app searches the public OpenStreetMap Overpass API; it does not require a Google Maps key or a billing account. Search starts at 15 miles and expands to 30 and 60 miles when fewer than three stations are found. It stops at three results or the 60-mile limit. The displayed distance unit follows Settings; Overpass requests use meters internally.

Precise phone location and internet access are required for a location-based search. The app can use a fresh fix, then a recent known location. When neither is available, it labels the fallback search center; it does not present that center as a live vehicle position. A station result is a mapped point or area center with a straight-line distance. It does not establish road-route distance, fuel grade, price, hours, or reachability.

A request sends a rounded location to the public Overpass service. The service is community-hosted, has usage limits, and can be unavailable. The app retries transient busy/network failures up to five times with increasing waits. Search also has a shared cooldown. Attribution is shown in the app; see [OpenStreetMap copyright](https://www.openstreetmap.org/copyright) and the [Overpass usage policy](https://wiki.openstreetmap.org/wiki/Overpass_API).

Search manually with **Find gas**. Automatic search is controlled by the configured fuel threshold and only runs while the phone app is visible. No live location is sent until a search is performed.

For build/install instructions and current feature behavior, see the [README](README.md). The Android Auto host controls map zoom and layout.
