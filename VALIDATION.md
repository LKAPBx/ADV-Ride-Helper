
## 0.135 — state migration and managed regression tests

Fuel baseline/economy and ride-distance fields now share a versioned preference file. A first-access migration copies existing values from the two legacy files and gives already-present destination values precedence. Fill-up, dashboard-reading, missed-distance, manual-distance, and MPG-only updates commit their related fuel/distance fields through one editor, reducing partial updates. Undo and JSON backup keep their existing group/schema format while reading and restoring the combined store. The legacy preference files are retained as a migration source.

Added Gradle-managed JUnit coverage for fuel/ride migration precedence, fuel math and distance replacement, bounded GPS gap estimates, station search expansion, and TPMS display bands. These checks do not exercise Android process death, backup restore on device, Android Auto host rendering, or actual sensor protocols. The full Gradle build and phone/head-unit validation remain required in Android Studio.
