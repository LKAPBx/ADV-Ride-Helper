package dev.africatwin.telemetry

import android.content.Context
import android.graphics.*
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat

internal object TpmsGraphics {
    fun drawColumns(context: Context, canvas: Canvas, top: Float) {
        val store = TpmsStore(context)
        val units = AppUnits.get(context)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD }
        listOf("front", "rear").forEachIndexed { index, wheel ->
            val left = 4f + index * 256f
            val id = store.assigned(wheel)
            val reading = store.reading(id)
            val stale = store.stale(id)
            val warning = if (stale) 0 else store.warning(wheel)
            paint.style = Paint.Style.FILL; paint.color = Color.rgb(25, 38, 31)
            canvas.drawRoundRect(left, top, left + 248f, top + 120f, 12f, 12f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
            paint.color = when { stale -> Color.GRAY; warning == 2 -> Color.rgb(235,65,65); warning == 1 -> Color.rgb(240,195,40); else -> Color.rgb(90,175,125) }
            canvas.drawRoundRect(left, top, left + 248f, top + 120f, 12f, 12f, paint)
            paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.LEFT; paint.color = Color.WHITE; paint.textSize = 26f
            canvas.drawText(if (index == 0) "Front" else "Rear", left + 12f, top + 31f, paint)
            val battery = TireBatteryView(context, true).apply { percent = reading?.first?.experimentalBatteryPercent; this.stale = stale; layout(0,0,66,36) }
            canvas.save(); canvas.translate(left + 170f, top + 3f); battery.draw(canvas); canvas.restore()
            fun line(value: String, y: Float, size: Float) {
                paint.textSize = size
                if (paint.measureText(value) > 224f) paint.textSize *= 224f / paint.measureText(value)
                canvas.drawText(value, left + 12f, top + y, paint)
            }
            line(reading?.first?.let { "${units.pressure(it.psi)} • ${units.temperature(it.celsius)}" } ?: if (id.isEmpty()) "Not assigned" else "Waiting", 71f, 28f)
            val state = when { stale -> "STALE • "; warning == 2 -> "ZERO • "; warning == 1 -> "LOW • "; else -> "" }
            line(state + (reading?.let { TpmsAge.label(System.currentTimeMillis() - it.second).removePrefix("Stale • ") } ?: ""), 104f, 22f)
        }
    }

    fun create(context: Context, compact: Boolean = false): CarIcon {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
        val store = TpmsStore(context)
        listOf("front", "rear").forEachIndexed { index, wheel ->
            val top = index * 256f
            val color = when (store.warning(wheel)) { 2 -> Color.rgb(235, 65, 65); 1 -> Color.rgb(240, 195, 40); else -> Color.rgb(90, 175, 125) }
            paint.style = Paint.Style.FILL; paint.color = Color.rgb(25, 38, 31)
            canvas.drawRoundRect(10f, top + 10, 502f, top + 246, 22f, 22f, paint)
            paint.style = Paint.Style.STROKE; paint.strokeWidth = if (compact) 26f else 8f; paint.color = color
            canvas.drawRoundRect(18f, top + 18, 494f, top + 238, 18f, 18f, paint)
            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.LEFT; paint.color = Color.WHITE
            paint.textSize = 38f; canvas.drawText(if (wheel == "front") "Front" else "Rear", 38f, top + 67, paint)
            val battery = TireBatteryView(context, true).apply {
                percent = store.reading(store.assigned(wheel))?.first?.experimentalBatteryPercent
                stale = store.stale(store.assigned(wheel)); layout(0, 0, 88, 48)
            }
            canvas.save(); canvas.translate(370f, top + 28f); battery.draw(canvas); canvas.restore()
            store.wheel(wheel).lines().take(2).forEachIndexed { line, value ->
                paint.color = Color.WHITE; paint.textSize = if (line == 0) 40f else 30f
                if (paint.measureText(value) > 432f) paint.textSize *= 432f / paint.measureText(value)
                canvas.drawText(value, 38f, top + 135 + line * 62, paint)
            }
        }
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }
}
