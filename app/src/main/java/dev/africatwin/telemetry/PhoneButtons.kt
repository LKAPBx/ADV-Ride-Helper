package dev.africatwin.telemetry

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.widget.Button

internal object PhoneButtons {
    fun style(button: Button, dark: Boolean) {
        val density = button.resources.displayMetrics.density
        fun shape(color: Int) = GradientDrawable().apply {
            setColor(color); cornerRadius = 18 * density
            setStroke((density).toInt().coerceAtLeast(1), if (dark) Color.rgb(90, 140, 120) else Color.rgb(35, 100, 75))
        }
        val states = StateListDrawable().apply {
            addState(intArrayOf(-android.R.attr.state_enabled), shape(if (dark) 0xff35433d.toInt() else 0xffd6dfda.toInt()))
            addState(intArrayOf(android.R.attr.state_checked), shape(0xff9f3535.toInt()))
            addState(intArrayOf(), shape(if (dark) 0xff275741.toInt() else 0xff1d6746.toInt()))
        }
        button.backgroundTintList = null
        button.background = RippleDrawable(ColorStateList.valueOf(0x45ffffff), states, shape(Color.WHITE))
        button.setTextColor(ColorStateList(arrayOf(intArrayOf(-android.R.attr.state_enabled), intArrayOf()),
            intArrayOf(if (dark) 0xffa4b0a9.toInt() else 0xff65736a.toInt(), Color.WHITE)))
        button.isAllCaps = false; button.textSize = 16f; button.typeface = Typeface.DEFAULT_BOLD
        button.minHeight = (54 * density).toInt(); button.minimumHeight = (54 * density).toInt()
        button.setPadding((18 * density).toInt(), (12 * density).toInt(), (18 * density).toInt(), (12 * density).toInt())
        button.elevation = 3 * density
        button.layoutParams = android.widget.LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = (5 * density).toInt(); bottomMargin = (5 * density).toInt()
        }
    }
}
