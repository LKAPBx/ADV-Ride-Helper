package dev.africatwin.telemetry

import android.content.Context
import android.graphics.*
import android.view.View

internal class TireBatteryView(context: Context, private val dark: Boolean) : View(context) {
    var percent: Int? = null
        set(value) { field = value?.takeIf { it in 0..100 }; updateDescription(); invalidate() }
    var stale = false
        set(value) { field = value; updateDescription(); invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun updateDescription() {
        contentDescription = "Sensor battery: ${percent?.let { "$it percent" } ?: "unknown"}${if (stale) ", stale" else ""}"
    }
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES; updateDescription() }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save(); canvas.scale(width / 44f, height / 24f)
        val ink = if (dark) Color.rgb(226, 237, 230) else Color.rgb(28, 49, 39)
        paint.color = ink; paint.alpha = if (stale) 110 else 255
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
        canvas.drawRoundRect(1f, 3f, 39f, 21f, 2f, 2f, paint)
        paint.style = Paint.Style.FILL
        canvas.drawRect(40f, 8f, 43f, 16f, paint)
        val bars = TireBatteryLevel.bars(percent)
        if (bars == null) {
            paint.textSize = 15f; paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("?", 20f, 17f, paint)
        } else for (i in 0..3) {
            paint.color = if (i < bars) ink else if (dark) Color.rgb(63, 77, 68) else Color.rgb(195, 208, 199)
            paint.alpha = if (stale) 110 else 255
            canvas.drawRect(4f + i * 8.5f, 6f, 10f + i * 8.5f, 18f, paint)
        }
        canvas.restore()
    }
}
