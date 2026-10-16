package dev.africatwin.telemetry

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat

internal object DashboardPanel {
    fun create(estimate: FuelEstimateStore, heading: Heading?): CarIcon {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(25, 35, 30))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val fuel = FuelBars.gaugeBitmap(estimate)
        val compass = requireNotNull(CompassIcon.create(heading?.degrees).icon!!.bitmap)
        // Separate stacked panels preserve aspect ratio and allocate more pixels to each gauge.
        paint.color = Color.rgb(20, 32, 25)
        canvas.drawRoundRect(4f, 4f, 508f, 252f, 16f, 16f, paint)
        paint.color = Color.rgb(23, 29, 38)
        canvas.drawRoundRect(4f, 260f, 508f, 508f, 16f, 16f, paint)
        if (fuel != null) canvas.drawBitmap(fuel, null, RectF(112f, 8f, 400f, 188f), paint)
        else {
            paint.color = Color.WHITE; paint.textAlign = Paint.Align.CENTER; paint.textSize = 48f
            canvas.drawText("—", 256f, 110f, paint)
        }
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        fun text(value: String, y: Float, size: Float, color: Int = Color.WHITE) {
            paint.color = color; paint.textSize = size
            if (paint.measureText(value) > 476f) paint.textSize *= 476f / paint.measureText(value)
            canvas.drawText(value, 256f, y, paint)
        }
        text("${estimate.fuelText()} • ${estimate.rangeText()}", 234f, 44f)
        canvas.drawBitmap(compass, null, RectF(12f, 274f, 232f, 494f), paint)
        fun beside(value: String, y: Float, size: Float) {
            paint.color = Color.WHITE; paint.textSize = size
            if (paint.measureText(value) > 244f) paint.textSize *= 244f / paint.measureText(value)
            canvas.drawText(value, 374f, y, paint)
        }
        beside("HEADING", 302f, 22f)
        beside(heading?.let { "${LiveHeading.cardinal(it.degrees)} ${it.degrees.toInt()}°" } ?: "—", 350f, 38f)
        beside("GPS SPEED", 410f, 22f)
        beside(LiveSpeed.current()?.let { estimate.units.speed(RideSafety.speedMph(it.metersPerSecond)!!) } ?: "— ${estimate.units.speedUnit}", 462f, 44f)
        paint.color = Color.rgb(110, 135, 120); paint.strokeWidth = 3f; paint.style = Paint.Style.STROKE
        canvas.drawRoundRect(4f, 4f, 508f, 252f, 16f, 16f, paint)
        canvas.drawRoundRect(4f, 260f, 508f, 508f, 16f, 16f, paint)
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }
}
