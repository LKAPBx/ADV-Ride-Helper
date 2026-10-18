package dev.africatwin.telemetry

import android.content.Context
import android.content.SharedPreferences

/** Keeps fuel baseline and ride distance in one preference file so related writes can commit together. */
internal object FuelRideState {
    private const val STORE = "fuel_ride_state"
    private const val VERSION_KEY = "_storageVersion"
    private const val VERSION = 1

    @Synchronized
    fun preferences(context: Context): SharedPreferences {
        val app = context.applicationContext
        val target = app.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        if (target.getInt(VERSION_KEY, 0) < VERSION) {
            val migrated = mergeLegacy(
                app.getSharedPreferences("fuel_estimate", Context.MODE_PRIVATE).all,
                app.getSharedPreferences("ride_tracking", Context.MODE_PRIVATE).all,
                target.all
            )
            val edit = target.edit().clear()
            migrated.forEach { (key, value) ->
                when (value) {
                    is String -> edit.putString(key, value)
                    is Boolean -> edit.putBoolean(key, value)
                    is Int -> edit.putInt(key, value)
                    is Long -> edit.putLong(key, value)
                    is Float -> edit.putFloat(key, value)
                    else -> Unit
                }
            }
            check(edit.putInt(VERSION_KEY, VERSION).commit()) { "Could not migrate fuel and ride state" }
        }
        return target
    }

    /** Pure merge rule used during migration: keep the old values, then prefer already-written new values. */
    internal fun mergeLegacy(fuel: Map<String, *>, ride: Map<String, *>, current: Map<String, *>): Map<String, Any> {
        val result = LinkedHashMap<String, Any>()
        (fuel + ride).forEach { (key, value) -> if (value is String || value is Boolean || value is Int || value is Long || value is Float) result[key] = value }
        current.forEach { (key, value) -> if (key != VERSION_KEY && (value is String || value is Boolean || value is Int || value is Long || value is Float)) result[key] = value }
        result[VERSION_KEY] = VERSION
        return result
    }
}
