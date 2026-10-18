package dev.africatwin.telemetry

import android.content.Context

/** Temporary owner/testing shortcut. Remove by setting AVAILABLE=false in a future release. */
internal object DeveloperOptions {
    const val AVAILABLE = true
    fun adsDisabled(context: Context) = AVAILABLE && context.getSharedPreferences("developer_options", Context.MODE_PRIVATE).getBoolean("disableAds", false)
    fun setAdsDisabled(context: Context, disabled: Boolean) {
        context.getSharedPreferences("developer_options", Context.MODE_PRIVATE).edit().putBoolean("disableAds", disabled).apply()
    }
}
