package com.ghostassist

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.abs

class OverlayService : Service() {
    companion object { @Volatile var instance: OverlayService? = null }

    private lateinit var wm: WindowManager
    private var button: TextView? = null
    private var hl: HighlightView? = null
    private var grabber: ScreenGrabber? = null
    private var reader: ScreenReader? = null
    private val bg = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        instance = this
        val ch = "ghost_assist"
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(ch, "Ghost Assist", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, ch).setContentTitle("Ghost Assist đang chạy")
            .setContentText("Bấm nút 👻 nổi để phân tích bàn cờ").setSmallIcon(android.R.drawable.ic_menu_view).build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else startForeground(1, n)

        val code = i?.getIntExtra("code", 0) ?: 0
        @Suppress("DEPRECATION") val data = i?.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection = mpm.getMediaProjection(code, data)
            ?: run { stopSelf(); return START_NOT_STICKY }
        grabber = ScreenGrabber(this, projection)
        reader = ScreenReader(this)
        addViews()
        return START_NOT_STICKY
    }

    private fun addViews() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY

        hl = HighlightView(this)
        // Phủ đúng toàn bộ màn hình thật (cùng khung với ảnh chụp), kể cả vùng thanh trạng thái / tai thỏ.
        val sw = grabber?.width ?: WindowManager.LayoutParams.MATCH_PARENT
        val sh = grabber?.height ?: WindowManager.LayoutParams.MATCH_PARENT
        hl?.refW = if (sw > 0) sw else 0; hl?.refH = if (sh > 0) sh else 0
        val hlp = WindowManager.LayoutParams(
            sw, sh, type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START; x = 0; y = 0
            if (Build.VERSION.SDK_INT >= 30)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            else if (Build.VERSION.SDK_INT >= 28)
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        wm.addView(hl, hlp)

        val lp = WindowManager.LayoutParams(
            140, 140, type, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 400 }
        button = TextView(this).apply {
            text = "👻"; textSize = 26f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xCC5B3FD1.toInt()) }
            var dx = 0f; var dy = 0f; var sx = 0; var sy = 0; var moved = false
            setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { dx = e.rawX; dy = e.rawY; sx = lp.x; sy = lp.y; moved = false }
                    MotionEvent.ACTION_MOVE -> {
                        if (abs(e.rawX - dx) > 12 || abs(e.rawY - dy) > 12) moved = true
                        if (moved) { lp.x = sx + (e.rawX - dx).toInt(); lp.y = sy + (e.rawY - dy).toInt(); wm.updateViewLayout(this, lp) }
                    }
                    MotionEvent.ACTION_UP -> if (!moved) analyze()
                }
                true
            }
        }
        wm.addView(button, lp)
    }

    private fun analyze() {
        hl?.showMessage("Đang phân tích…")
        // ẩn lớp phủ + nút để không lọt vào ảnh chụp
        ui.postDelayed({
            hl?.visibility = View.INVISIBLE; button?.visibility = View.INVISIBLE
            ui.postDelayed({
                bg.execute {
                    var out: JSONObject? = null; var err: String? = null
                    try {
                        val bmp = grabber?.captureOnce()
                        if (bmp == null) err = "Không chụp được màn hình, thử lại."
                        else out = reader?.parse(bmp)
                    } catch (t: Throwable) { err = "Lỗi đọc màn hình: ${t.message}" }
                    ui.post {
                        hl?.visibility = View.VISIBLE; button?.visibility = View.VISIBLE
                        if (out != null && GhostAssistModule.instance != null) GhostAssistModule.instance!!.emitBoard(out.toString())
                        else hl?.showMessage(err ?: "Ứng dụng chính chưa sẵn sàng, hãy mở lại app.")
                    }
                }
            }, 200)
        }, 50)
    }

    fun showSuggestion(json: String) = ui.post { hl?.showSuggestion(JSONObject(json)) }

    fun learn(kind: String, zone: String, a: Int, b: Int, done: (Boolean) -> Unit) {
        bg.execute {
            val bmp = grabber?.captureOnce()
            if (bmp != null) reader?.learnTemplate(bmp, kind, zone, a, b)
            ui.post { done(bmp != null) }
        }
    }

    override fun onDestroy() {
        instance = null
        runCatching { wm.removeView(button) }; runCatching { wm.removeView(hl) }
        grabber?.release()
        super.onDestroy()
    }
}
