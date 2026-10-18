package dev.africatwin.telemetry

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView

/** Layout preview only. No ad requests, tracking SDK, price, or purchase entitlement. */
internal object PhoneAdSpace {
    fun create(activity: Activity, dark: Boolean): LinearLayout {
        fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
        val ink = if (dark) Color.rgb(226, 237, 230) else Color.rgb(28, 49, 39)
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(52); setPadding(dp(12), dp(2), dp(6), dp(2))
            background = GradientDrawable().apply {
                setColor(if (dark) Color.rgb(36, 46, 40) else Color.rgb(230, 237, 232))
                cornerRadius = dp(8).toFloat()
            }
        }
        row.addView(TextView(activity).apply {
            text = "Ad space • preview"; textSize = 13f; setTextColor(ink)
            contentDescription = "Advertisement area preview. No live advertisement."
        }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(activity).apply {
            text = "Full version"; textSize = 13f; setTextColor(ink); typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER; minHeight = dp(48); setPadding(dp(10), 0, dp(10), 0)
            isFocusable = true; isClickable = true
            contentDescription = "Full version information"
            setOnClickListener { showInfo(activity) }
        }, LinearLayout.LayoutParams(-2, -2))
        return row
    }
    fun showInfo(activity: Activity) {
        AlertDialog.Builder(activity).setTitle("Full version")
            .setMessage("The full version will remove the phone banner ads with a one-time purchase. Purchases are not available yet. This banner is a layout preview; no ads are currently loaded.")
            .setPositiveButton("Close", null).show()
    }
}
