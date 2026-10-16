package dev.africatwin.telemetry

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.*
import android.media.ImageReader
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Size
import android.view.*
import android.widget.*
import kotlin.math.abs

/** Saved guide rectangles also define the optional recognition crops in the upright photo. */
class DashboardCameraActivity : Activity() {
    private lateinit var texture: TextureView
    private lateinit var shutter: Button
    private lateinit var instructions: TextView
    private lateinit var previewFrame: FrameLayout
    private lateinit var guides: DashboardCropView
    private var previewSize: Size? = null
    private var previewRatio = 0.75f
    private var output: Uri? = null
    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var previewRequest: CaptureRequest.Builder? = null
    private val handler = Handler(Looper.getMainLooper())
    private val worker = HandlerThread("DashboardPhoto")
    private lateinit var workerHandler: Handler
    private var resumed = false
    private var opening = false
    private var taking = false
    private var sensorOrientation = 90
    private var focusMode = CaptureRequest.CONTROL_AF_MODE_OFF
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        output = intent.getParcelableExtra(MediaStore.EXTRA_OUTPUT)
        if (output == null) { finish(); return }
        worker.start(); workerHandler = Handler(worker.looper)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        instructions = TextView(this).apply {
            text = "Park first. Align Economy / Trip and TOTAL odometer with the boxes. Avoid glare and hold steady."
            setTextColor(Color.WHITE); textSize = 17f; setPadding(20, 16, 20, 16)
        }
        root.addView(instructions)
        val area = FrameLayout(this)
        val preview = object : FrameLayout(this) {
            override fun onMeasure(widthSpec: Int, heightSpec: Int) {
                val maxWidth = MeasureSpec.getSize(widthSpec); val maxHeight = MeasureSpec.getSize(heightSpec)
                val width = minOf(maxWidth, (maxHeight * previewRatio).toInt())
                super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec((width / previewRatio).toInt(), MeasureSpec.EXACTLY))
            }
        }
        previewFrame = preview
        texture = TextureView(this)
        preview.addView(texture, FrameLayout.LayoutParams(-1, -1))
        guides = DashboardCropView(this)
        preview.addView(guides, FrameLayout.LayoutParams(-1, -1))
        area.addView(preview, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        root.addView(area, LinearLayout.LayoutParams(-1, 0, 1f))
        shutter = Button(this).apply { text = "Take dashboard photo"; isAllCaps = false; isEnabled = false; setOnClickListener { focusAndCapture() } }
        root.addView(shutter)
        val guideControls = LinearLayout(this)
        val reset = Button(this).apply {
            text = "Reset boxes"; isAllCaps = false; visibility = View.GONE
            setOnClickListener { guides.reset() }
        }
        guideControls.addView(Button(this).apply {
            text = "Adjust boxes"; isAllCaps = false
            setOnClickListener {
                if (taking) return@setOnClickListener
                if (guides.editing) guides.save()
                guides.editing = !guides.editing
                text = if (guides.editing) "Save boxes" else "Adjust boxes"
                reset.visibility = if (guides.editing) View.VISIBLE else View.GONE
                shutter.isEnabled = !guides.editing && session != null
                instructions.text = if (guides.editing) "Drag a box to move it. Drag its bottom-right dot to resize. Include the labels and digits."
                    else "Boxes saved. Align the dashboard, avoid glare and hold steady."
            }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        guideControls.addView(reset, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(guideControls)
        root.addView(Button(this).apply { text = "Use phone camera instead"; isAllCaps = false; setOnClickListener { fallback() } })
        root.addView(Button(this).apply { text = "Cancel"; isAllCaps = false; setOnClickListener { finish() } })
        root.setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        setContentView(root); root.requestApplyInsets()
        texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) { openCamera() }
            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) { transformPreview() }
            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { closeCamera(); return true }
            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.CAMERA), 1)
    }
    override fun onResume() { super.onResume(); resumed = true; openCamera() }
    override fun onPause() { resumed = false; handler.removeCallbacksAndMessages(null); closeCamera(); super.onPause() }
    override fun onDestroy() { if (worker.isAlive) worker.quitSafely(); super.onDestroy() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == 1) {
            if (results.firstOrNull() == PackageManager.PERMISSION_GRANTED) openCamera()
            else instructions.text = "Camera permission is needed to take photos. Cancel to choose an existing image, or grant permission and try again."
        }
    }
    private fun openCamera() {
        if (!resumed || !texture.isAvailable || device != null || opening || checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) return
        try {
            val manager = getSystemService(CameraManager::class.java)
            val id = manager.cameraIdList.first { manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK }
            val characteristics = manager.getCameraCharacteristics(id)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val modes = characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: intArrayOf()
            focusMode = when {
                modes.contains(CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE) -> CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE
                modes.contains(CaptureRequest.CONTROL_AF_MODE_AUTO) -> CaptureRequest.CONTROL_AF_MODE_AUTO
                else -> CaptureRequest.CONTROL_AF_MODE_OFF
            }
            val sizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val jpegSizes = sizes.getOutputSizes(ImageFormat.JPEG)
            val fourThirds = jpegSizes.filter { abs(it.width.toDouble() / it.height - 4.0 / 3) < 0.03 }
            val moderate = fourThirds.filter { it.width.toLong() * it.height <= 16_000_000 }
            val jpeg = (moderate.ifEmpty { fourThirds }.ifEmpty { jpegSizes.toList() }).maxBy { it.width.toLong() * it.height }
            val available = sizes.getOutputSizes(SurfaceTexture::class.java)
            val previewSize = available.filter { it.width <= 1920 && abs(it.width.toDouble() / it.height - jpeg.width.toDouble() / jpeg.height) < 0.03 }
                .maxByOrNull { it.width.toLong() * it.height } ?: available.minBy { abs(it.width.toDouble() / it.height - jpeg.width.toDouble() / jpeg.height) }
            texture.surfaceTexture!!.setDefaultBufferSize(previewSize.width, previewSize.height)
            this.previewSize = previewSize
            val relative = (sensorOrientation - displayDegrees() + 360) % 360
            previewRatio = if (relative % 180 == 0) previewSize.width.toFloat() / previewSize.height else previewSize.height.toFloat() / previewSize.width
            previewFrame.requestLayout(); texture.post { transformPreview() }
            reader = ImageReader.newInstance(jpeg.width, jpeg.height, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener({ source ->
                    runCatching { source.acquireNextImage() }.getOrNull()?.use { image ->
                        val result = runCatching {
                            val buffer = image.planes[0].buffer; val bytes = ByteArray(buffer.remaining()); buffer.get(bytes)
                            check(bytes.isNotEmpty())
                            contentResolver.openOutputStream(output!!, "w")!!.use { it.write(bytes) }
                        }
                        runOnUiThread {
                            if (!isFinishing && !isDestroyed) {
                                if (result.isSuccess) { setResult(RESULT_OK); finish() } else fail("Could not save photo. Try the phone camera.")
                            }
                        }
                    }
                }, workerHandler)
            }
            opening = true
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    opening = false
                    if (!resumed) { camera.close(); return }
                    device = camera
                    try {
                        val surface = Surface(texture.surfaceTexture!!); previewSurface = surface
                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                            addTarget(surface); set(CaptureRequest.CONTROL_AF_MODE, focusMode)
                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                            set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                        }; previewRequest = request
                        camera.createCaptureSession(listOf(surface, reader!!.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(created: CameraCaptureSession) {
                                if (!resumed || device !== camera) { created.close(); return }
                                session = created
                                try { created.setRepeatingRequest(request.build(), null, handler); shutter.isEnabled = !guides.editing }
                                catch (_: CameraAccessException) { fail("Preview unavailable. Try the phone camera.") }
                            }
                            override fun onConfigureFailed(created: CameraCaptureSession) { fail("Camera setup failed. Try the phone camera.") }
                        }, handler)
                    } catch (_: Exception) { closeCamera(); fail("Camera preview unavailable. Try the phone camera.") }
                }
                override fun onDisconnected(camera: CameraDevice) { camera.close(); device = null; opening = false; fail("Camera disconnected") }
                override fun onError(camera: CameraDevice, error: Int) { camera.close(); device = null; opening = false; fail("Camera unavailable. Try the phone camera.") }
            }, handler)
        } catch (_: Exception) { opening = false; fail("Camera unavailable. Try the phone camera.") }
    }
    private fun focusAndCapture() {
        if (taking || session == null || guides.editing) return
        taking = true; shutter.isEnabled = false; instructions.text = "Focusing… hold steady"
        try {
            previewRequest?.let { request ->
                if (focusMode != CaptureRequest.CONTROL_AF_MODE_OFF) {
                    request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_START)
                    session!!.capture(request.build(), null, handler)
                    request.set(CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.CONTROL_AF_TRIGGER_IDLE)
                }
            }
            handler.postDelayed({ capture() }, 700)
        } catch (_: Exception) { fail("Could not focus. Try again or use phone camera.") }
    }
    private fun capture() {
        if (!resumed) return
        try {
            val request = device!!.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(reader!!.surface); set(CaptureRequest.CONTROL_AF_MODE, focusMode)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
                val rotation = displayDegrees()
                set(CaptureRequest.JPEG_ORIENTATION, (sensorOrientation - rotation + 360) % 360)
                set(CaptureRequest.JPEG_QUALITY, 95.toByte())
            }
            session!!.capture(request.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) { fail("Photo failed. Try again.") }
            }, handler)
        } catch (_: Exception) { fail("Could not capture photo. Try the phone camera.") }
    }
    private fun fail(text: String) { instructions.text = text; taking = false; shutter.isEnabled = session != null }
    private fun displayDegrees() = when (windowManager.defaultDisplay.rotation) {
        Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0
    }
    private fun transformPreview() {
        val size = previewSize ?: return
        if (texture.width == 0 || texture.height == 0) return
        // TextureView applies sensor orientation; compensate for display rotation and preserve aspect.
        val uprightWidth = if (sensorOrientation % 180 == 0) size.width.toFloat() else size.height.toFloat()
        val uprightHeight = if (sensorOrientation % 180 == 0) size.height.toFloat() else size.width.toFloat()
        val rotation = displayDegrees()
        val rotatedWidth = if (rotation % 180 == 0) uprightWidth else uprightHeight
        val rotatedHeight = if (rotation % 180 == 0) uprightHeight else uprightWidth
        val scale = minOf(texture.width / rotatedWidth, texture.height / rotatedHeight)
        val cx = texture.width / 2f; val cy = texture.height / 2f
        val target = RectF(cx - uprightWidth * scale / 2, cy - uprightHeight * scale / 2,
            cx + uprightWidth * scale / 2, cy + uprightHeight * scale / 2)
        val matrix = Matrix()
        matrix.setRectToRect(RectF(0f, 0f, texture.width.toFloat(), texture.height.toFloat()), target, Matrix.ScaleToFit.FILL)
        matrix.postRotate(-rotation.toFloat(), cx, cy)
        texture.setTransform(matrix)
    }
    private fun closeCamera() {
        shutter.isEnabled = false; taking = false
        session?.close(); session = null; device?.close(); device = null
        reader?.close(); reader = null; previewSurface?.release(); previewSurface = null; previewRequest = null
    }
    private fun fallback() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 1); return
        }
        try {
            startActivityForResult(Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, output)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                .apply { clipData = android.content.ClipData.newRawUri("Dashboard photo", output!!) }, 2)
        } catch (_: Exception) { instructions.text = "Phone camera unavailable. Cancel and choose an existing photo." }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 2) { setResult(resultCode); finish() }
    }
}


