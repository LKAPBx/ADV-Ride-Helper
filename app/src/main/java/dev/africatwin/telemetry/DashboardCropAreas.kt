package dev.africatwin.telemetry

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** Fractions of the upright photo, shared by the preview, editor and recognition crops. */
internal object DashboardCropAreas {
    // Exact normalized rectangles supplied by the user's 0.96 crop-coordinate report.
    fun defaults() = listOf(RectF(0f, .238163f, .317969f, .548774f),
        RectF(.519219f, .621313f, .716646f, .712435f))
    fun load(context: Context): List<RectF> {
        val prefs = context.getSharedPreferences("dashboard_crops", Context.MODE_PRIVATE)
        return defaults().mapIndexed { index, fallback ->
            val values = prefs.getString("box$index", null)?.split(',')?.mapNotNull { it.toFloatOrNull() }
            if (values?.size != 4) fallback else RectF(values[0], values[1], values[2], values[3]).takeIf {
                values.all { value -> value.isFinite() && value in 0f..1f } && it.width() >= .04f && it.height() >= .04f
            } ?: fallback
        }
    }
    fun save(context: Context, boxes: List<RectF>) {
        val edit = context.getSharedPreferences("dashboard_crops", Context.MODE_PRIVATE).edit()
        boxes.forEachIndexed { index, box -> edit.putString("box$index", "${box.left},${box.top},${box.right},${box.bottom}") }
        edit.putBoolean("configured", true).apply()
    }
    fun configured(context: Context) = context.getSharedPreferences("dashboard_crops", Context.MODE_PRIVATE).getBoolean("configured", false)
    fun report(context: Context, boxes: List<RectF> = load(context), width: Int? = null, height: Int? = null): String {
        val prefs = context.getSharedPreferences("dashboard_crops", Context.MODE_PRIVATE)
        val w = width ?: prefs.getInt("photoWidth", 0)
        val h = height ?: prefs.getInt("photoHeight", 0)
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        return buildString {
            appendLine("ADV Ride Helper $version • OCR crop coordinates")
            appendLine(if (width != null) "Source: current editor boxes (may be unsaved)" else "Source: saved boxes/defaults; custom OCR enabled: ${configured(context)}")
            appendLine("Origin: top-left of upright photo; excludes screen controls and letterboxing. Fractions: 0–1.")
            appendLine(if (w > 0 && h > 0) "${if (width != null) "Current" else "Last decoded"} OCR image: ${w}×${h} px (decoded/resized, not original JPEG)." else "Photo dimensions unavailable until a photo is read.")
            boxes.forEachIndexed { index, box ->
                appendLine(if (index == 0) "Economy / Trip:" else "TOTAL odometer:")
                appendLine(String.format(java.util.Locale.US, "  left=%.6f top=%.6f right=%.6f bottom=%.6f width=%.6f height=%.6f", box.left, box.top, box.right, box.bottom, box.width(), box.height()))
                appendLine(String.format(java.util.Locale.US, "  Percent: x=%.2f%% y=%.2f%% width=%.2f%% height=%.2f%%", box.left * 100, box.top * 100, box.width() * 100, box.height() * 100))
                if (w > 0 && h > 0) {
                    val left = (box.left * w).toInt().coerceIn(0, w - 1)
                    val top = (box.top * h).toInt().coerceIn(0, h - 1)
                    val right = (box.right * w).toInt().coerceIn(left + 1, w)
                    val bottom = (box.bottom * h).toInt().coerceIn(top + 1, h)
                    appendLine("  Pixel crop: x=$left y=$top width=${right-left} height=${bottom-top} right=$right bottom=$bottom (right/bottom exclusive)")
                }
            }
        }
    }
    fun showReport(activity: android.app.Activity, boxes: List<RectF> = load(activity), width: Int? = null, height: Int? = null) {
        val report = report(activity, boxes, width, height)
        val text = android.widget.TextView(activity).apply { this.text = report; setTextIsSelectable(true); setPadding(24, 12, 24, 12) }
        android.app.AlertDialog.Builder(activity).setTitle("OCR crop coordinates")
            .setView(android.widget.ScrollView(activity).apply { addView(text) })
            .setPositiveButton("Copy report") { _, _ ->
                activity.getSystemService(android.content.ClipboardManager::class.java)
                    .setPrimaryClip(android.content.ClipData.newPlainText("OCR crop coordinates", report))
                android.widget.Toast.makeText(activity, "Crop coordinates copied", android.widget.Toast.LENGTH_SHORT).show()
            }.setNegativeButton("Close", null).show()
    }
}

internal class DashboardCropView(context: Context, private val photo: Bitmap? = null) : View(context) {
    var boxes = DashboardCropAreas.load(context)
        private set
    var editing = false
        set(value) { field = value; invalidate() }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val frame = RectF()
    private var selected = -1
    private var resize = false
    private var lastX = 0f
    private var lastY = 0f
    fun reset() { boxes = DashboardCropAreas.defaults(); invalidate() }
    fun save() = DashboardCropAreas.save(context, boxes)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        frame.set(0f, 0f, width.toFloat(), height.toFloat())
        photo?.let {
            val scale = minOf(width.toFloat() / it.width, height.toFloat() / it.height)
            val w = it.width * scale; val h = it.height * scale
            frame.set((width - w) / 2, (height - h) / 2, (width + w) / 2, (height + h) / 2)
            paint.colorFilter = null; paint.style = Paint.Style.FILL
            canvas.drawBitmap(it, null, frame, paint)
        }
        val density = resources.displayMetrics.density
        boxes.forEachIndexed { index, box ->
            val rect = pixels(box)
            paint.color = if (index == 0) Color.CYAN else Color.YELLOW
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 3 * density
            canvas.drawRoundRect(rect, 6 * density, 6 * density, paint)
            paint.style = Paint.Style.FILL; paint.textSize = 14 * density; paint.typeface = Typeface.DEFAULT_BOLD
            canvas.drawText(if (index == 0) "Economy / Trip" else "TOTAL odometer", rect.left + 4 * density,
                maxOf(frame.top + 16 * density, rect.top - 5 * density), paint)
            if (editing) canvas.drawCircle(rect.right, rect.bottom, 10 * density, paint)
        }
    }
    private fun pixels(box: RectF) = RectF(frame.left + box.left * frame.width(), frame.top + box.top * frame.height(),
        frame.left + box.right * frame.width(), frame.top + box.bottom * frame.height())
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!editing || frame.width() <= 0 || frame.height() <= 0) return false
        val x = (event.x - frame.left) / frame.width(); val y = (event.y - frame.top) / frame.height()
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val radius = 28 * resources.displayMetrics.density
                selected = boxes.indices.reversed().firstOrNull { index ->
                    val rect = pixels(boxes[index]); rect.inset(-radius, -radius); rect.contains(event.x, event.y)
                } ?: -1
                if (selected < 0) return false
                val rect = pixels(boxes[selected])
                resize = kotlin.math.abs(event.x - rect.right) < radius && kotlin.math.abs(event.y - rect.bottom) < radius
                lastX = x; lastY = y; parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (selected < 0) return false
                val rect = RectF(boxes[selected])
                if (resize) {
                    rect.right = (rect.right + x - lastX).coerceIn(rect.left + .04f, 1f)
                    rect.bottom = (rect.bottom + y - lastY).coerceIn(rect.top + .04f, 1f)
                } else rect.offset((x - lastX).coerceIn(-rect.left, 1f - rect.right),
                    (y - lastY).coerceIn(-rect.top, 1f - rect.bottom))
                boxes = boxes.mapIndexed { index, box -> if (index == selected) rect else box }
                lastX = x; lastY = y; invalidate(); return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                selected = -1; parent?.requestDisallowInterceptTouchEvent(false); performClick(); return true
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
}
