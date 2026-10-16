> Historical guide: some controls and version-specific steps below have changed. See [the current README](README.md) for version 0.69 behavior and installation.

# Fill-up photos and fuel estimates — version 0.8

1. Sync the project in Studio to download the bundled Latin OCR dependency. Generate a signed release AAB, upload code 8 to Internal testing, roll out and update from Play.
2. On the phone open Tank size & options. Confirm tank size (default 5 US gallons); changes affect the next fill-up, not an existing baseline.
3. After filling the tank completely, tap Fill up > Take dashboard photo. Capture the odometer and average MPG clearly while parked. An existing photo or manual entry is also available.
4. Review the photo and suggested readings. Correct both fields and confirm that units are miles and US MPG. Only Confirm records the full-tank baseline.
5. Later use Update dashboard reading and another photo. The app retains the original fill-up odometer, calculates total miles from its difference, and recalculates consumption using the updated average MPG.

Average MPG is used, never instantaneous MPG. For best results the dashboard's averaging period should match the fill-up interval. A full-tank button assumes actual full capacity; a partial refill cannot be represented accurately by this initial workflow.

The initial parser searches for ODO/ODOMETER labels or TOTAL with distance units, and AVG/AVERAGE with MPG. It rejects trip distance, instantaneous consumption, ambiguous multiple readings and explicit metric/Imperial units rather than guessing conversions. Dashboard layout recognition is intentionally conservative. Values without recognizable labels may require manual entry. OCR may miss decimal points, separators, labels or digits; even a suggested value must be checked.

Photos are processed on the phone using the bundled ML Kit model. The external camera saves a full image to a narrowly exposed cache folder through a non-exported FileProvider. Processing uses a rotated/scaled software bitmap with a longest edge at most 2048 pixels. No broad photo-storage or direct camera permission is requested; the camera app and system document picker handle access.

The review dialog includes an image preview and Show recognized text. Cancel leaves estimates unchanged. The odometer must be at least the current fill-up baseline, and MPG must be positive. Empty/failed OCR falls back to manual fields. Once confirmed, the baseline or latest reading is saved locally; photographs are not needed for later calculation.

Optional GPS under Options tracks distance between confirmed photos. Stop GPS before recording a reading. A confirmed photo corrects the GPS distance total and updates MPG; no double counting is added to that correction. GPS can then restart explicitly. Estimates may be too optimistic after missed tracking, idling or poorer fuel economy. Range shown is to estimated empty, not a guaranteed safe range and not a reserve-adjusted figure.

## Tests still needed on the phone

- Sync/build/link ML Kit, then test camera and gallery selections.
- Photograph the real dash with miles/US average MPG; compare suggestions carefully.
- Cancel capture and cancel review: baseline and totals must remain unchanged.
- Try trip-only, instantaneous-only, metric, blurry and glared photos: unresolved fields should remain blank.
- Record one fill-up, then a later reading and revised MPG: verify baseline stays fixed and the formula recalculates.
- Rotate the phone during capture/review; interrupted or unreadable images should permit manual entry.
- Verify new permissions no longer include car telemetry access, and Android Auto displays estimates rather than unknown host data.

Pure parser/GPS/fuel arithmetic checks pass. UI-cleanup source compilation passed before OCR integration. Final Android compilation/lint and the runtime cases above have not been verified in this session. Send a representative parked-dashboard photo if recognition needs tuning.

