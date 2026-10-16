package dev.africatwin.telemetry

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

internal class FuelGaugeView(context: Context, private val dark: Boolean) : View(context) {
    var fraction: Double? = null
        set(value) { field = value?.takeIf { it.isFinite() }?.coerceIn(0.0, 1.0); invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val density = resources.displayMetrics.density
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height - 34f * density
        val radius = minOf(width / 2f - 22f * density, cy - 12f * density).coerceAtLeast(1f)
        val arc = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val ink = if (dark) Color.rgb(226, 237, 230) else Color.rgb(28, 49, 39)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 12f * density; paint.strokeCap = Paint.Cap.BUTT
        paint.color = if (dark) Color.rgb(60, 72, 66) else Color.rgb(225, 231, 228)
        canvas.drawArc(arc, 180f, 180f, false, paint)
        if (fraction != null) {
            paint.color = Color.rgb(200, 55, 55); canvas.drawArc(arc, 180f, 18f, false, paint)
            paint.color = Color.rgb(210, 140, 35); canvas.drawArc(arc, 198f, 27f, false, paint)
            paint.color = Color.rgb(35, 150, 90); canvas.drawArc(arc, 225f, 135f, false, paint)
        }
        paint.strokeWidth = 2f * density; paint.color = ink
        for (i in 0..4) {
            val angle = Math.toRadians(180.0 + 45.0 * i)
            canvas.drawLine(cx + cos(angle).toFloat() * (radius - 15f * density), cy + sin(angle).toFloat() * (radius - 15f * density),
                cx + cos(angle).toFloat() * (radius - 25f * density), cy + sin(angle).toFloat() * (radius - 25f * density), paint)
        }
        fraction?.let { amount ->
            val angle = Math.toRadians(180.0 + 180.0 * amount)
            paint.strokeWidth = 4f * density; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(cx, cy, cx + cos(angle).toFloat() * (radius - 30f * density), cy + sin(angle).toFloat() * (radius - 30f * density), paint)
            paint.style = Paint.Style.FILL; canvas.drawCircle(cx, cy, 7f * density, paint)
        }
        paint.style = Paint.Style.FILL; paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textSize = 16f * resources.displayMetrics.scaledDensity
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("E", cx - radius, cy + 24f * density, paint)
        canvas.drawText("F", cx + radius, cy + 24f * density, paint)
        canvas.drawText(fraction?.let { "${(it * 100).toInt()}%" } ?: "No reading", cx, cy + 28f * density, paint)
    }
}
