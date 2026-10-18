# Phone banner and full version setup

Version 0.123 adds a small, scrollable phone-only banner layout preview at the top of the main screen. Full version opens an honest not-yet-available message. No ad SDK, ad request, charge, fake entitlement or purchase flow exists yet. Do not advertise the upgrade as available in the store listing until integration and testing are complete.

## AdMob

1. Create an AdMob account and register the Android app with package `dev.africatwin.telemetry` and the existing Play listing.
2. Create a banner ad unit. Keep its app ID and banner ad-unit ID for integration (these IDs are not private signing keys).
3. Configure applicable privacy messages in AdMob Privacy & messaging; maintain a privacy policy and complete Play Data safety / Contains ads disclosures for the actual SDK integration.
4. Add the current supported Google Mobile Ads SDK and UMP consent SDK. Obtain updated consent information on each app launch, show required consent forms and offer privacy choices when required. Only request ads when allowed; do not treat a missing form as consent.
5. Replace the preview with a width-adaptive inline banner suited to this scrollable layout. Leave space between the ad and app controls; no overlays or accidental-tap targets. Collapse the ad area if unavailable. Keep the upgrade link outside the ad's clickable view.
6. Use Google's dedicated test banner unit during development and license testing; do not click your own live ads. Set real IDs only for the intended production configuration.

https://developers.google.com/admob/android/banner
https://developers.google.com/admob/android/privacy
https://developers.google.com/admob/android/test-ads

## One-time full version

1. In Play Console, configure the payments profile and create a one-time product. Suggested product ID: `full_app` (choose before integration; product IDs cannot simply be renamed).
2. Set the price, countries/regions and an active purchase option as required by the current Console UI. Product presentation: Full version, removes phone banner ads. No subscription required.
3. Integrate the currently supported Play Billing Library, using the same application/package. Obtain localized price and availability from ProductDetails, not hardcoded text.
4. Verify PURCHASED state, product and purchase authenticity before granting entitlement. Pending/canceled/failed purchases do not unlock. Acknowledge non-consumable purchases; never consume this product.
5. Persist a verified entitlement cache for offline use; reconcile purchases on launch/resume and provide Restore purchases. Handle refunds/revocations and service errors; do not revoke cached access solely because an offline query failed. Prefer server verification and real-time developer notifications for reliable revocation handling.
6. For an entitled user, remove the entire banner area and stop all ad loading. No manual local toggle should grant a paid entitlement.
7. Test through Play internal testing with license testers: purchase, pending, cancel, restore/reinstall, offline use and refund. SDK/resource packaging and device tests remain necessary.

https://developer.android.com/google/play/billing/integrate
https://developer.android.com/google/play/billing/test

## Car screens and widgets

No ad placement or upgrade promotion is added to the head-unit templates, notifications or ride widget. Keep tire alerts, tracking and driving displays separate from monetization.
https://developer.android.com/docs/quality-guidelines/car-app-quality

The account IDs, active product details and desired price are still needed to finish live monetization. This layout alone does not earn ad revenue or charge users.

## Temporary local developer override (0.124)
Tap the main phone screen's ADV Ride Helper title seven times, with less than 1.5 seconds between taps. Developer & advanced contains Disable phone ads on this device plus diagnostic tools. Sensor assignment remains in regular Settings.
The override is local, survives app restart, is excluded from JSON backup and does not grant a Play entitlement. It is discoverable, not secure owner authentication. Do not market this as paid-access enforcement.
Set DeveloperOptions.AVAILABLE=false to disable both the entry gesture and stored override in a later release. Future ad integration must check DeveloperOptions.adsDisabled before SDK initialization/ad requests as well as hiding the banner. Current build still has only a preview.
