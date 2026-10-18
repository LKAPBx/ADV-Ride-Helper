package dev.africatwin.telemetry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Row
import androidx.core.graphics.drawable.IconCompat

internal object TireCarRow {
    fun build(context: Context, wheel: String, openDetails: (() -> Unit)? = null): Row {
        val store = TpmsStore(context)
        val percent = store.reading(store.assigned(wheel))?.first?.experimentalBatteryPercent
        val icon = TireBatteryView(context, true).apply {
            this.percent = percent; stale = store.stale(store.assigned(wheel)); layout(0, 0, 88, 48)
        }
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap); canvas.translate(4f, 24f); icon.draw(canvas)
        val lines = store.wheel(wheel).lines()
        return Row.Builder().setTitle(if (wheel == "front") "Front tire" else "Rear tire")
            .addText(lines.firstOrNull() ?: "Waiting for sensor")
            .addText(lines.getOrNull(1).orEmpty())
            .setImage(CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build(), Row.IMAGE_TYPE_SMALL)
            .apply { if (openDetails != null) setBrowsable(true).setOnClickListener { openDetails() } }
            .build()
    }
}
