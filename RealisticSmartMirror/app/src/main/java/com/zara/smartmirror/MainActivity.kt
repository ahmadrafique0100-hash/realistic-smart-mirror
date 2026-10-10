package com.zara.smartmirror

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    private lateinit var root: FrameLayout
    private lateinit var previewView: PreviewView
    private lateinit var frozenImage: ImageView
    private lateinit var lightingOverlay: View
    private lateinit var controls: LinearLayout
    private lateinit var statusText: TextView
    private var camera: Camera? = null
    private var mirrored = true
    private var frozen = false
    private var controlsVisible = true
    private var zoom = 1f
    private var lightMode = 0 // 0 natural, 1 warm, 2 cool
    private var manualBrightness = false
    private var lastTap = 0L
    private var downX = 0f
    private var downY = 0f

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else showMessage("Camera permission is needed to use the mirror.")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        buildInterface()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    private fun buildInterface() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        previewView = PreviewView(this).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
            setBackgroundColor(Color.BLACK)
        }
        root.addView(previewView, FrameLayout.LayoutParams(-1, -1))

        frozenImage = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
            setBackgroundColor(Color.BLACK)
        }
        root.addView(frozenImage, FrameLayout.LayoutParams(-1, -1))

        lightingOverlay = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = false
        }
        root.addView(lightingOverlay, FrameLayout.LayoutParams(-1, -1))

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(8))
            background = rounded(Color.argb(145, 9, 14, 22), dp(18))
        }
        val title = TextView(this).apply {
            text = "SMART MIRROR"
            setTextColor(Color.WHITE)
            textSize = 13f
            letterSpacing = 0.18f
        }
        statusText = TextView(this).apply {
            text = "Natural reflection  •  Front camera"
            setTextColor(Color.rgb(207, 219, 233))
            textSize = 11f
            setPadding(0, dp(4), 0, 0)
        }
        top.addView(title)
        top.addView(statusText)
        val topParams = FrameLayout.LayoutParams(-1, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            setMargins(dp(14), dp(22), dp(14), 0)
        }
        top.tag = "topPanel"
        root.addView(top, topParams)

        controls = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(Color.argb(220, 10, 15, 23), dp(22))
        }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        row1.addView(makeButton("Mirror", "Mirror reversal on/off") { toggleMirror() }, weightedParams())
        row1.addView(makeButton("Freeze", "Freeze or resume reflection") { toggleFreeze() }, weightedParams())
        row1.addView(makeButton("Light", "Cycle natural, warm and cool light") { cycleLight() }, weightedParams())
        row1.addView(makeButton("Zoom −", "Zoom out") { setZoom(zoom - 0.25f) }, weightedParams())
        row1.addView(makeButton("Zoom +", "Zoom in") { setZoom(zoom + 0.25f) }, weightedParams())
        controls.addView(row1)

        val brightnessLabel = TextView(this).apply {
            text = "Screen brightness"
            setTextColor(Color.WHITE)
            textSize = 12f
            setPadding(dp(4), dp(10), 0, 0)
        }
        controls.addView(brightnessLabel)
        val brightnessRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val brightnessSeek = SeekBar(this).apply {
            max = 100
            progress = 75
            contentDescription = "Manual screen brightness"
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        manualBrightness = true
                        val params = window.attributes
                        params.screenBrightness = (progress.coerceAtLeast(5) / 100f)
                        window.attributes = params
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        brightnessRow.addView(brightnessSeek, LinearLayout.LayoutParams(0, dp(36), 1f))
        brightnessRow.addView(makeButton("Auto", "Return to system brightness") {
            manualBrightness = false
            val params = window.attributes
            params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            window.attributes = params
            showMessage("System brightness restored")
        })
        controls.addView(brightnessRow)

        val bottomHint = TextView(this).apply {
            text = "Tap the reflection to hide controls  •  Pinch to zoom  •  Double-tap to freeze"
            setTextColor(Color.rgb(195, 205, 218))
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(8), dp(4), 0)
        }
        controls.addView(bottomHint)
        val controlParams = FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply {
            setMargins(dp(12), 0, dp(12), dp(18))
        }
        root.addView(controls, controlParams)

        val gestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                setZoom(zoom * detector.scaleFactor)
                return true
            }
        })
        val mirrorTouchListener = View.OnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = event.x; downY = event.y; true }
                MotionEvent.ACTION_UP -> {
                    val dx = event.x - downX
                    val dy = event.y - downY
                    if (kotlin.math.abs(dx) < dp(18) && kotlin.math.abs(dy) < dp(18) && !gestureDetector.isInProgress) {
                        val now = System.currentTimeMillis()
                        if (now - lastTap < 320) {
                            toggleFreeze(); lastTap = 0L
                        } else {
                            lastTap = now
                            toggleControls()
                        }
                    }
                    true
                }
                else -> true
            }
        }
        root.setOnTouchListener(mirrorTouchListener)
        previewView.setOnTouchListener(mirrorTouchListener)
        setContentView(root)
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                provider.unbindAll()
                camera = provider.bindToLifecycle(this, CameraSelector.DEFAULT_FRONT_CAMERA, preview)
                previewView.scaleX = if (mirrored) -1f else 1f
                statusText.text = "Ready  •  ${if (mirrored) "Mirror mode" else "Camera mode"}"
            } catch (e: Exception) {
                showMessage("Could not start camera: ${e.localizedMessage ?: "unknown error"}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun toggleMirror() {
        mirrored = !mirrored
        // Scale only the live preview; frozen images preserve the captured appearance.
        previewView.scaleX = if (mirrored) -1f else 1f
        statusText.text = if (mirrored) "Mirror mode  •  Left-right reversed" else "Camera mode  •  Normal orientation"
    }

    private fun toggleFreeze() {
        if (!frozen) {
            val bitmap = try { previewView.bitmap } catch (_: Exception) { null }
            if (bitmap == null) {
                showMessage("Freeze is not ready yet. Try again in a moment.")
                return
            }
            frozenImage.setImageBitmap(bitmap)
            frozenImage.scaleX = if (mirrored) -1f else 1f
            frozenImage.visibility = View.VISIBLE
            frozen = true
            statusText.text = "Reflection frozen  •  Tap Freeze or double-tap to resume"
        } else {
            frozenImage.setImageDrawable(null)
            frozenImage.visibility = View.GONE
            frozen = false
            statusText.text = if (mirrored) "Live mirror mode" else "Live camera mode"
        }
    }

    private fun cycleLight() {
        lightMode = (lightMode + 1) % 3
        when (lightMode) {
            0 -> { lightingOverlay.setBackgroundColor(Color.TRANSPARENT); statusText.text = "Natural light mode" }
            1 -> { lightingOverlay.setBackgroundColor(Color.argb(28, 255, 174, 92)); statusText.text = "Warm screen tint (visual effect)" }
            2 -> { lightingOverlay.setBackgroundColor(Color.argb(24, 150, 205, 255)); statusText.text = "Cool screen tint (visual effect)" }
        }
    }

    private fun setZoom(value: Float) {
        zoom = value.coerceIn(1f, 4f)
        camera?.cameraControl?.setZoomRatio(zoom)
        statusText.text = "Zoom ${"%.1f".format(zoom)}×  •  Pinch to adjust"
    }

    private fun toggleControls() {
        controlsVisible = !controlsVisible
        controls.visibility = if (controlsVisible) View.VISIBLE else View.GONE
        root.findViewWithTag<View>("topPanel")?.visibility = if (controlsVisible) View.VISIBLE else View.GONE
    }

    private fun makeButton(label: String, description: String, action: () -> Unit): Button = Button(this).apply {
        text = label
        contentDescription = description
        textSize = 10f
        isAllCaps = false
        setTextColor(Color.WHITE)
        background = rounded(Color.rgb(38, 53, 72), dp(12))
        minHeight = dp(42)
        minimumHeight = dp(42)
        setPadding(dp(4), 0, dp(4), 0)
        setOnClickListener { action() }
    }

    private fun weightedParams() = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
        setMargins(dp(2), 0, dp(2), 0)
    }

    private fun rounded(color: Int, radius: Int): android.graphics.drawable.GradientDrawable =
        android.graphics.drawable.GradientDrawable().apply { setColor(color); cornerRadius = radius.toFloat() }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun showMessage(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}
