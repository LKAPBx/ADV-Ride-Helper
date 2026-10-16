package dev.africatwin.telemetry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

internal data class DashboardScan(val preview: Bitmap, val text: String, val readings: DashboardReadings)

internal object DashboardPhotoReader {
    fun read(context: Context, uri: Uri, complete: (Result<DashboardScan>) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        Thread {
            try {
                val image = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val factor = minOf(1.0, 2048.0 / maxOf(info.size.width, info.size.height))
                    decoder.setTargetSize(maxOf(1, (info.size.width * factor).toInt()), maxOf(1, (info.size.height * factor).toInt()))
                }
                val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                context.getSharedPreferences("dashboard_crops", Context.MODE_PRIVATE).edit()
                    .putInt("photoWidth", image.width).putInt("photoHeight", image.height).apply()
                try {
                    recognizer.process(InputImage.fromBitmap(image, 0))
                        .addOnSuccessListener { result ->
                            readAreas(context, image, recognizer, DashboardTextParser.parse(result.text, AppUnits.photoEconomy(context))) { readings, areaText ->
                            val recognizedText = result.text + areaText
                            val lines = result.textBlocks.flatMap { it.lines }
                            val totals = lines.filter { Regex("\\bTOTAL\\b", RegexOption.IGNORE_CASE).containsMatchIn(it.text) }
                            val label = totals.singleOrNull()?.boundingBox
                            val miles = Regex("\\bMILE[S]?\\b", RegexOption.IGNORE_CASE).containsMatchIn(result.text)
                            if (readings.odometerMiles != null || label == null || !miles || label.width() <= 0 || label.height() <= 0) {
                                recognizer.close(); complete(Result.success(DashboardScan(image, recognizedText, readings)))
                            } else {
                                val digitLeft = (label.left - 2 * label.width()).coerceAtLeast(0)
                                val digitRight = (label.left - 2).coerceIn(digitLeft, image.width)
                                val digitTop = (label.top - label.height()).coerceAtLeast(0)
                                val digitBottom = (label.bottom + 2 * label.height()).coerceAtMost(image.height)
                                val dw = digitRight - digitLeft; val dh = digitBottom - digitTop
                                val segmented = if (dw > 0 && dh > 0) {
                                    val pixels = IntArray(dw * dh)
                                    image.getPixels(pixels, 0, dw, digitLeft, digitTop, dw, dh)
                                    val gray = pixels.map { pixel -> ((pixel shr 16 and 255) + (pixel shr 8 and 255) + (pixel and 255)) / 3 }.toIntArray()
                                    SegmentOdometer.read(dw, dh, gray)
                                } else null
                                // The lower display's small digits are left of TOTAL, not adjacent in OCR text order.
                                val left = (label.left - 3 * label.width()).coerceAtLeast(0)
                                val top = (label.top - label.height()).coerceAtLeast(0)
                                val right = (label.right + label.width() / 2).coerceAtMost(image.width)
                                val bottom = (label.bottom + label.height()).coerceAtMost(image.height)
                                val crop = Bitmap.createBitmap(image, left, top, right - left, bottom - top)
                                val enlarged = Bitmap.createScaledBitmap(crop, crop.width * 4, crop.height * 4, true)
                                val enhanced = Bitmap.createBitmap(enlarged.width, enlarged.height, Bitmap.Config.ARGB_8888)
                                val matrix = ColorMatrix().apply { setSaturation(0f) }
                                matrix.postConcat(ColorMatrix(floatArrayOf(1.5f,0f,0f,0f,-40f, 0f,1.5f,0f,0f,-40f, 0f,0f,1.5f,0f,-40f, 0f,0f,0f,1f,0f)))
                                Canvas(enhanced).drawBitmap(enlarged, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
                                fun finishCrop(cropText: String, odo: Double? = null) {
                                    val resolved = if (odo != null && segmented != null && odo != segmented) null else odo ?: segmented
                                    val revised = if (resolved == null) readings else readings.copy(odometerMiles = resolved,
                                        notes = "Odometer suggested from the TOTAL-mile digit area. Verify every digit against the photo before saving.")
                                    recognizer.close(); if (crop !== image) crop.recycle(); enlarged.recycle(); enhanced.recycle()
                                    complete(Result.success(DashboardScan(image, recognizedText + "\n\nTOTAL-mile crop (second pass):\n" + cropText + "\nSegment-digit suggestion: ${segmented ?: "unavailable"}", revised)))
                                }
                                recognizer.process(InputImage.fromBitmap(enhanced, 0))
                                    .addOnSuccessListener { cropped ->
                                        val elements = cropped.textBlocks.flatMap { it.lines }.flatMap { it.elements }
                                        val total = elements.filter { it.text.equals("TOTAL", true) }.singleOrNull()?.boundingBox
                                        val candidates = if (total == null) emptyList() else elements.filter { element ->
                                            val box = element.boundingBox
                                            box != null && Regex("(?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?").matches(element.text) &&
                                                box.right <= total.left + 4 &&
                                                kotlin.math.abs(box.exactCenterY() - total.exactCenterY()) <= maxOf(box.height(), total.height()).toFloat()
                                        }.map { it.text }
                                        finishCrop(cropped.text, DashboardTextParser.parseOdometerCrop(candidates.joinToString("\n")))
                                    }
                                    .addOnFailureListener { finishCrop("") }
                            }
                            }
                        }.addOnFailureListener { error -> recognizer.close(); image.recycle(); complete(Result.failure(error)) }
                } catch (e: Exception) { recognizer.close(); image.recycle(); throw e }
            } catch (e: Exception) { handler.post { complete(Result.failure(e)) } }
        }.start()
    }

    /** Extra passes use saved upright image fractions, never arbitrary text/digits as an odometer. */
    private fun readAreas(context: Context, image: Bitmap, recognizer: TextRecognizer,
        original: DashboardReadings, complete: (DashboardReadings, String) -> Unit) {
        if (!DashboardCropAreas.configured(context)) { complete(original, ""); return }
        val boxes = DashboardCropAreas.load(context)
        var readings = original
        val text = StringBuilder()
        fun next(index: Int) {
            if (index >= boxes.size) { complete(readings, text.toString()); return }
            val box = boxes[index]
            val left = (box.left * image.width).toInt().coerceIn(0, image.width - 1)
            val top = (box.top * image.height).toInt().coerceIn(0, image.height - 1)
            val right = (box.right * image.width).toInt().coerceIn(left + 1, image.width)
            val bottom = (box.bottom * image.height).toInt().coerceIn(top + 1, image.height)
            val crop = Bitmap.createBitmap(image, left, top, right - left, bottom - top)
            val scale = minOf(4f, 2048f / maxOf(crop.width, crop.height))
            val enlarged = Bitmap.createScaledBitmap(crop, maxOf(1, (crop.width * scale).toInt()),
                maxOf(1, (crop.height * scale).toInt()), true)
            val enhanced = Bitmap.createBitmap(enlarged.width, enlarged.height, Bitmap.Config.ARGB_8888)
            val matrix = ColorMatrix().apply { setSaturation(0f) }
            matrix.postConcat(ColorMatrix(floatArrayOf(1.5f,0f,0f,0f,-40f, 0f,1.5f,0f,0f,-40f,
                0f,0f,1.5f,0f,-40f, 0f,0f,0f,1f,0f)))
            Canvas(enhanced).drawBitmap(enlarged, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
            fun release() {
                enhanced.recycle()
                if (enlarged !== crop && enlarged !== image) enlarged.recycle()
                if (crop !== image) crop.recycle()
            }
            try {
                recognizer.process(InputImage.fromBitmap(enhanced, 0)).addOnSuccessListener { result ->
                    text.append("\n\nSaved ${if (index == 0) "Economy / Trip" else "TOTAL"} crop (up to 4×):\n${result.text}")
                    val parsed = DashboardTextParser.parse(result.text, AppUnits.photoEconomy(context))
                    readings = readings.copy(
                        odometerMiles = readings.odometerMiles ?: if (index == 1) parsed.odometerMiles else null,
                        averageMpg = readings.averageMpg ?: if (index == 0) parsed.averageMpg else null,
                        tripMiles = readings.tripMiles ?: if (index == 0) parsed.tripMiles else null,
                        tripMeter = readings.tripMeter ?: if (index == 0) parsed.tripMeter else null,
                        notes = readings.notes + "\nSaved crop checked. Verify all suggested values against the photo.")
                    release(); next(index + 1)
                }.addOnFailureListener {
                    text.append("\nSaved crop ${index + 1}: recognition unavailable")
                    release(); next(index + 1)
                }
            } catch (_: RuntimeException) { release(); next(index + 1) }
        }
        next(0)
    }
}
