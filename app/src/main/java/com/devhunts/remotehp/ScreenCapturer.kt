package com.devhunts.remotehp

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream

/**
 * Mirrors the screen via MediaProjection and emits JPEG frames (throttled).
 */
class ScreenCapturer(
    context: Context,
    private val projection: MediaProjection,
    private val onFrame: (ByteArray) -> Unit
) {
    private val thread = HandlerThread("capture").apply { start() }
    private val handler = Handler(thread.looper)

    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null

    private val realW: Int
    private val realH: Int
    private val dpi: Int
    private val capW: Int
    private val capH: Int

    private var lastFrameMs = 0L
    private val minIntervalMs = 66L // ~15 fps
    private val jpegQuality = 45

    init {
        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        wm.defaultDisplay.getRealMetrics(metrics)
        realW = metrics.widthPixels
        realH = metrics.heightPixels
        dpi = metrics.densityDpi
        // scale down big screens to keep the stream light
        val scale = if (realW > 800) 800f / realW else 1f
        capW = (realW * scale).toInt().let { it - (it % 2) }
        capH = (realH * scale).toInt().let { it - (it % 2) }
    }

    fun start() {
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stop()
            }
        }, handler)

        imageReader = ImageReader.newInstance(capW, capH, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ reader -> onImage(reader) }, handler)
        }

        virtualDisplay = projection.createVirtualDisplay(
            "remotehp",
            capW, capH, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface,
            null,
            handler
        )
    }

    private fun onImage(reader: ImageReader) {
        var image: Image? = null
        try {
            image = reader.acquireLatestImage() ?: return
            val now = System.currentTimeMillis()
            if (now - lastFrameMs < minIntervalMs) return
            lastFrameMs = now

            val plane = image.planes[0]
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * capW
            val bmpW = capW + rowPadding / pixelStride

            val bmp = Bitmap.createBitmap(bmpW, capH, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(buffer)
            val cropped = if (bmpW != capW) Bitmap.createBitmap(bmp, 0, 0, capW, capH) else bmp

            val baos = ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, jpegQuality, baos)
            onFrame(baos.toByteArray())

            if (cropped !== bmp) cropped.recycle()
            bmp.recycle()
        } catch (_: Exception) {
        } finally {
            image?.close()
        }
    }

    fun stop() {
        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null
        try {
            imageReader?.close()
        } catch (_: Exception) {
        }
        imageReader = null
        thread.quitSafely()
    }
}
