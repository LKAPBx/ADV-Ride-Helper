package dev.africatwin.telemetry

import android.content.Context
import android.graphics.*
import androidx.car.app.model.CarIcon
import androidx.core.graphics.drawable.IconCompat

internal object TpmsGraphics {
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
            if (compact) {
                paint.textSize = 152f; canvas.drawText(if (index == 0) "F" else "R", 256f, top + 184, paint)
            } else {
                paint.textSize = 34f; canvas.drawText(wheel.uppercase(), 256f, top + 62, paint)
                store.wheel(wheel).lines().take(2).forEachIndexed { line, value ->
                    paint.color = Color.WHITE; paint.textSize = if (line == 0) 36f else 28f
                    if (paint.measureText(value) > 448f) paint.textSize *= 448f / paint.measureText(value)
                    canvas.drawText(value, 256f, top + 126 + line * 60, paint)
                }
            }
        }
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }
}
