package com.ghostassist

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Android 14+ chỉ cho tạo 1 VirtualDisplay / MediaProjection, nên ta giữ 1 display liên tục
 * và chỉ chuyển khung hình thành Bitmap khi được yêu cầu (wantFrame).
 */
class ScreenGrabber(ctx: Context, private val projection: MediaProjection) {
    val width: Int
    val height: Int
    private val thread = HandlerThread("ghost-grab").also { it.start() }
    private val handler = Handler(thread.looper)
    private val reader: ImageReader
    private val display: VirtualDisplay

    @Volatile private var wantFrame = false
    @Volatile private var result: Bitmap? = null
    @Volatile private var latch: CountDownLatch? = null

    init {
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
        width = dm.widthPixels; height = dm.heightPixels

        projection.registerCallback(object : MediaProjection.Callback() {}, handler)
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val createdDisplay = projection.createVirtualDisplay(
            "ghost-assist", width, height, dm.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, handler
        ) ?: throw IllegalStateException("Không tạo được VirtualDisplay")
        display = createdDisplay
        reader.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                if (wantFrame) {
                    val p = img.planes[0]
                    val rowPadding = p.rowStride - p.pixelStride * width
                    val bmp = Bitmap.createBitmap(width + rowPadding / p.pixelStride, height, Bitmap.Config.ARGB_8888)
                    bmp.copyPixelsFromBuffer(p.buffer)
                    result = if (rowPadding == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, width, height)
                    wantFrame = false
                    latch?.countDown()
                }
            } finally { img.close() }
        }, handler)
    }

    /** Gọi từ thread nền. Trả null nếu quá 1.5s không có khung hình mới (màn hình đứng im). */
    fun captureOnce(): Bitmap? {
        result = null
        latch = CountDownLatch(1)
        wantFrame = true
        latch!!.await(1500, TimeUnit.MILLISECONDS)
        wantFrame = false
        return result
    }

    fun release() {
        runCatching { display.release() }
        runCatching { reader.close() }
        runCatching { projection.stop() }
        thread.quitSafely()
    }
}
