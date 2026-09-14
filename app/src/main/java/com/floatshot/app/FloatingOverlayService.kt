package com.floatshot.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlin.math.abs

class FloatingOverlayService : Service() {

    companion object {
        const val ACTION_START = "com.floatshot.app.START"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_RESULT_DATA = "resultData"
        const val CHANNEL_ID = "floatshot_channel"
        const val NOTIF_ID = 101
        const val PREFS = "floatshot_prefs"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaProjection: MediaProjection? = null

    private var iconView: View? = null
    private var frameView: View? = null
    private var frameParams: WindowManager.LayoutParams? = null
    private var settingsView: View? = null

    private val handler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_START) {
            startForeground(NOTIF_ID, buildNotification())

            val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
            val resultData: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            if (resultData != null) {
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, resultData)
                mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                    override fun onStop() {
                        mediaProjection = null
                    }
                }, handler)
            }
            showFloatingIcon()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        removeIcon()
        removeFrame()
        removeSettingsPopup()
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "FloatShot", NotificationManager.IMPORTANCE_LOW
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, FloatingOverlayService::class.java).apply { action = "STOP" }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FloatShot chal raha hai")
            .setContentText("Floating icon active hai — tap karke screenshot lein")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(0, "Stop", stopPending)
            .setOngoing(true)
            .build()
    }

    // ---------------- FLOATING ICON ----------------

    private fun showFloatingIcon() {
        if (iconView != null) return
        val iv = ImageView(this).apply {
            setImageResource(R.drawable.ic_camera)
            setPadding(14, 14, 14, 14)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#3F51B5"))
            }
        }
        iconView = iv

        val size = dp(56)
        val params = WindowManager.LayoutParams(
            size, size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = prefs.getInt("icon_x", 0)
        params.y = prefs.getInt("icon_y", 200)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        iv.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 8 || abs(dy) > 8) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(iv, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putInt("icon_x", params.x).putInt("icon_y", params.y).apply()
                    if (!moved) toggleFrame()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(iv, params)
    }

    private fun removeIcon() {
        iconView?.let { runCatching { windowManager.removeView(it) } }
        iconView = null
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    // ---------------- CAPTURE FRAME (adjustable square) ----------------

    private fun toggleFrame() {
        if (frameView != null) removeFrame() else showFrame()
    }

    private fun showFrame() {
        val root = LayoutInflater.from(this).inflate(R.layout.floating_frame, null)
        frameView = root

        root.background = GradientDrawable().apply {
            setStroke(dp(3), Color.parseColor("#00E5FF"))
            setColor(Color.parseColor("#22000000"))
        }

        val defaultSize = dp(280)
        val w = prefs.getInt("frame_w", defaultSize)
        val h = prefs.getInt("frame_h", defaultSize)
        val x = prefs.getInt("frame_x", 100)
        val y = prefs.getInt("frame_y", 400)

        val params = WindowManager.LayoutParams(
            w, h,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = x
        params.y = y
        frameParams = params

        windowManager.addView(root, params)

        // Drag whole frame around
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = event.rawX; touchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putInt("frame_x", params.x).putInt("frame_y", params.y).apply()
                    true
                }
                else -> false
            }
        }

        val btnClose = root.findViewById<ImageButton>(R.id.btnClose)
        val btnCapture = root.findViewById<ImageButton>(R.id.btnCapture)
        val btnSettings = root.findViewById<ImageButton>(R.id.btnSettings)
        val btnResize = root.findViewById<ImageButton>(R.id.btnResize)

        btnClose.setOnClickListener { removeFrame() }
        btnCapture.setOnClickListener { performCapture() }
        btnSettings.setOnClickListener { toggleSettingsPopup() }

        // Drag bottom-right handle to freely resize the square
        var rInitSize = 0
        var rTouchX = 0f
        var rTouchY = 0f
        btnResize.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    rInitSize = params.width
                    rTouchX = event.rawX; rTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val delta = maxOf(event.rawX - rTouchX, event.rawY - rTouchY)
                    val newSize = (rInitSize + delta).toInt().coerceAtLeast(dp(100))
                    params.width = newSize
                    params.height = newSize
                    windowManager.updateViewLayout(root, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    prefs.edit().putInt("frame_w", params.width).putInt("frame_h", params.height).apply()
                    true
                }
                else -> false
            }
        }
    }

    private fun removeFrame() {
        frameView?.let { runCatching { windowManager.removeView(it) } }
        frameView = null
        frameParams = null
        removeSettingsPopup()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ---------------- BORDER SETTINGS POPUP ----------------

    private fun toggleSettingsPopup() {
        if (settingsView != null) {
            removeSettingsPopup()
            return
        }
        val view = LayoutInflater.from(this).inflate(R.layout.settings_popup, null)
        settingsView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.CENTER

        val switchBorder = view.findViewById<Switch>(R.id.switchBorder)
        val colorRow = view.findViewById<LinearLayout>(R.id.colorRow)
        val btnDone = view.findViewById<TextView>(R.id.btnDone)

        switchBorder.isChecked = prefs.getBoolean("border_enabled", true)
        switchBorder.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("border_enabled", checked).apply()
        }

        val colors = listOf("#FF0000", "#FF9800", "#FFEB3B", "#4CAF50", "#2196F3", "#FFFFFF", "#000000")
        val currentColor = prefs.getInt("border_color", Color.parseColor("#FF0000"))

        for (hex in colors) {
            val colorInt = Color.parseColor(hex)
            val swatch = View(this)
            val lp = LinearLayout.LayoutParams(dp(28), dp(28))
            lp.marginEnd = dp(8)
            swatch.layoutParams = lp
            swatch.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(colorInt)
                if (colorInt == currentColor) setStroke(dp(3), Color.CYAN)
            }
            swatch.setOnClickListener {
                prefs.edit().putInt("border_color", colorInt).apply()
                removeSettingsPopup()
                toggleSettingsPopup()
            }
            colorRow.addView(swatch)
        }

        btnDone.setOnClickListener { removeSettingsPopup() }

        windowManager.addView(view, params)
    }

    private fun removeSettingsPopup() {
        settingsView?.let { runCatching { windowManager.removeView(it) } }
        settingsView = null
    }

    // ---------------- SCREENSHOT CAPTURE ----------------

    private fun performCapture() {
        val projection = mediaProjection
        val fParams = frameParams
        if (projection == null || fParams == null) {
            Toast.makeText(this, "Capture permission missing, app dobara kholein", Toast.LENGTH_SHORT).show()
            return
        }

        iconView?.visibility = View.INVISIBLE
        frameView?.visibility = View.INVISIBLE
        settingsView?.visibility = View.INVISIBLE

        handler.postDelayed({
            captureFullScreen(projection) { fullBitmap ->
                try {
                    val cropped = cropToFrame(fullBitmap, fParams.x, fParams.y, fParams.width, fParams.height)
                    val borderEnabled = prefs.getBoolean("border_enabled", true)
                    val finalBitmap = if (borderEnabled) {
                        val color = prefs.getInt("border_color", Color.RED)
                        addBorder(cropped, color, dp(6))
                    } else cropped

                    val uri = saveBitmap(finalBitmap)
                    handler.post {
                        restoreUiVisibility()
                        if (uri != null) {
                            Toast.makeText(this, "Screenshot saved: Pictures/FloatShot", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this, "Save fail ho gaya", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    handler.post {
                        restoreUiVisibility()
                        Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }, 200)
    }

    private fun restoreUiVisibility() {
        iconView?.visibility = View.VISIBLE
        frameView?.visibility = View.VISIBLE
        settingsView?.visibility = View.VISIBLE
    }

    private fun captureFullScreen(projection: MediaProjection, onCaptured: (Bitmap) -> Unit) {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        val virtualDisplay: VirtualDisplay? = projection.createVirtualDisplay(
            "FloatShotCapture", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, handler
        )

        imageReader.setOnImageAvailableListener({ reader ->
            val image: Image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * width

            val bitmap = Bitmap.createBitmap(
                width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            virtualDisplay?.release()
            reader.close()

            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            onCaptured(cropped)
        }, handler)
    }

    private fun cropToFrame(full: Bitmap, left: Int, top: Int, w: Int, h: Int): Bitmap {
        val safeLeft = left.coerceIn(0, full.width - 1)
        val safeTop = top.coerceIn(0, full.height - 1)
        val safeW = w.coerceAtMost(full.width - safeLeft).coerceAtLeast(1)
        val safeH = h.coerceAtMost(full.height - safeTop).coerceAtLeast(1)
        return Bitmap.createBitmap(full, safeLeft, safeTop, safeW, safeH)
    }

    private fun addBorder(src: Bitmap, color: Int, widthPx: Int): Bitmap {
        val result = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0f, 0f, null)
        val paint = Paint().apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = widthPx.toFloat()
            isAntiAlias = true
        }
        val half = widthPx / 2f
        canvas.drawRect(half, half, src.width - half, src.height - half, paint)
        return result
    }

    private fun saveBitmap(bitmap: Bitmap): Uri? {
        val filename = "FloatShot_${System.currentTimeMillis()}.png"
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/FloatShot")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        return uri
    }
}
