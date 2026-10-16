package dev.africatwin.telemetry

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface




internal object FuelBars {
    fun fraction(estimate: FuelEstimateStore): Double? {
        val full = estimate.filledCapacity ?: return null
        val remaining = estimate.remaining ?: return null
        return if (full > 0 && full.isFinite()) (remaining / full).coerceIn(0.0, 1.0) else null
    }
    fun gaugeBitmap(estimate: FuelEstimateStore): Bitmap? {
        val amount = fraction(estimate) ?: return null
        // Row images work on the API-8 bike host; no API-9 progress component is sent.
        val bitmap = Bitmap.createBitmap(256, 160, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

            // Neutral card keeps text/needle legible in either host day/night mode.
            paint.color = Color.rgb(25, 35, 30)
            canvas.drawRoundRect(0f, 0f, 256f, 160f, 14f, 14f, paint)
            val arc = RectF(12f, 12f, 244f, 244f)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 16f
            paint.color = Color.rgb(200, 55, 55); canvas.drawArc(arc, 180f, 18f, false, paint)
            paint.color = Color.rgb(210, 140, 35); canvas.drawArc(arc, 198f, 27f, false, paint)
            paint.color = Color.rgb(35, 150, 90); canvas.drawArc(arc, 225f, 135f, false, paint)
            paint.color = Color.WHITE; paint.strokeWidth = 3f
            for (i in 0..4) {
                val angle = Math.toRadians(180.0 + i * 45.0)
                canvas.drawLine(128f + kotlin.math.cos(angle).toFloat() * 105f, 128f + kotlin.math.sin(angle).toFloat() * 105f,
                    128f + kotlin.math.cos(angle).toFloat() * 93f, 128f + kotlin.math.sin(angle).toFloat() * 93f, paint)
            }
            val needle = Math.toRadians(180.0 + amount * 180.0)
            paint.strokeWidth = 7f; paint.strokeCap = Paint.Cap.ROUND
            canvas.drawLine(128f, 128f, 128f + kotlin.math.cos(needle).toFloat() * 90f, 128f + kotlin.math.sin(needle).toFloat() * 90f, paint)
            paint.style = Paint.Style.FILL; canvas.drawCircle(128f, 128f, 7f, paint)
            paint.textAlign = Paint.Align.CENTER; paint.textSize = 25f; paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText("E", 22f, 153f, paint); canvas.drawText("F", 234f, 153f, paint)
            canvas.drawText("${(amount * 100).toInt()}%", 128f, 155f, paint)
            return bitmap
    }
}

