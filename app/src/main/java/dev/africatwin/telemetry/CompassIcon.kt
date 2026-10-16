package dev.africatwin.telemetry

import android.graphics.*


import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat


internal object CompassIcon {
    fun create(degrees: Double?): CarIcon {
        val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.scale(2f, 2f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(25, 35, 30); canvas.drawRoundRect(0f, 0f, 256f, 256f, 16f, 16f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
        paint.color = Color.rgb(130, 155, 145); canvas.drawCircle(128f, 128f, 112f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE; paint.textSize = 32f; paint.textAlign = Paint.Align.CENTER; paint.typeface = Typeface.DEFAULT_BOLD
        canvas.drawText("N", 128f, 36f, paint); canvas.drawText("S", 128f, 241f, paint)
        canvas.drawText("W", 24f, 138f, paint); canvas.drawText("E", 232f, 138f, paint)
        if (degrees == null) {
            paint.textSize = 40f; canvas.drawText("—", 128f, 142f, paint)
            return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
        }
        canvas.save(); canvas.rotate(degrees.toFloat(), 128f, 128f)
        paint.color = Color.rgb(230, 75, 60)
        canvas.drawPath(Path().apply { moveTo(128f, 36f); lineTo(104f, 133f); lineTo(152f, 133f); close() }, paint)
        paint.color = Color.rgb(170, 185, 180)
        canvas.drawPath(Path().apply { moveTo(128f, 220f); lineTo(104f, 123f); lineTo(152f, 123f); close() }, paint)
        canvas.restore(); paint.color = Color.WHITE; canvas.drawCircle(128f, 128f, 8f, paint)
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }
}


