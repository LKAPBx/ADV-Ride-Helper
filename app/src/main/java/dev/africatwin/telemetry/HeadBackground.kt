package dev.africatwin.telemetry

import android.content.Context
import android.graphics.*
import android.net.Uri
import android.util.AtomicFile
import java.io.File

/** Private, normalized copy: no ongoing gallery permission or per-frame decoding. */
internal object HeadBackground {
    private var cached: Bitmap? = null
    private var loaded = false
    private fun file(c: Context) = File(c.filesDir, "head-background.png")
    fun revision(c: Context) = c.getSharedPreferences("head_background", Context.MODE_PRIVATE).getLong("revision", 0)
    fun image(c: Context): Bitmap? {
        if (!loaded) { cached = BitmapFactory.decodeFile(file(c).path); loaded = true }
        return cached
    }
    fun save(c: Context, uri: Uri) {
        val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(c.contentResolver, uri)) { decoder, info, _ ->
            require(info.size.width > 0 && info.size.height > 0)
            val scale = minOf(1.0, 1024.0 / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        val normalized = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(normalized)
        val scale = maxOf(512f / decoded.width, 512f / decoded.height)
        val w = decoded.width * scale; val h = decoded.height * scale
        canvas.drawBitmap(decoded, null, RectF((512-w)/2, (512-h)/2, (512+w)/2, (512+h)/2), Paint(Paint.FILTER_BITMAP_FLAG))
        decoded.recycle()
        val target = AtomicFile(file(c))
        val output = target.startWrite()
        try {
            check(normalized.compress(Bitmap.CompressFormat.PNG, 100, output))
            target.finishWrite(output)
        } catch (error: Exception) { target.failWrite(output); normalized.recycle(); throw error }
        cached = normalized; loaded = true
        changed(c)
    }
    fun clear(c: Context) {
        AtomicFile(file(c)).delete(); cached = null; loaded = true; changed(c)
    }
    private fun changed(c: Context) {
        val prefs = c.getSharedPreferences("head_background", Context.MODE_PRIVATE)
        prefs.edit().putLong("revision", prefs.getLong("revision", 0) + 1).apply()
    }
}
