package com.devhunts.remotehp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import androidx.core.content.ContextCompat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Ambil satu foto dari kamera (tanpa preview). Butuh izin CAMERA. */
object CameraSnap {

    fun capture(ctx: Context?, back: Boolean, timeoutMs: Long = 7000): ByteArray? {
        if (ctx == null) return null
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED) return null

        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val wantFacing = if (back) CameraCharacteristics.LENS_FACING_BACK
        else CameraCharacteristics.LENS_FACING_FRONT

        var camId: String? = null
        try {
            for (id in cm.cameraIdList) {
                if (cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == wantFacing) {
                    camId = id; break
                }
            }
            camId = camId ?: cm.cameraIdList.firstOrNull()
        } catch (_: Exception) {}
        if (camId == null) return null

        val thread = HandlerThread("camsnap").apply { start() }
        val handler = Handler(thread.looper)

        val sizes = try {
            cm.getCameraCharacteristics(camId)
                .get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                ?.getOutputSizes(ImageFormat.JPEG)
        } catch (_: Exception) { null }
        val size = sizes?.minByOrNull { Math.abs(it.width * it.height - 1280 * 720) } ?: Size(1280, 720)

        val reader = ImageReader.newInstance(size.width, size.height, ImageFormat.JPEG, 1)
        val latch = CountDownLatch(1)
        var result: ByteArray? = null

        reader.setOnImageAvailableListener({ r ->
            try {
                val img = r.acquireLatestImage()
                if (img != null) {
                    val buf = img.planes[0].buffer
                    val b = ByteArray(buf.remaining()); buf.get(b); result = b; img.close()
                }
            } catch (_: Exception) {}
            latch.countDown()
        }, handler)

        var device: CameraDevice? = null
        try {
            cm.openCamera(camId, object : CameraDevice.StateCallback() {
                override fun onOpened(cd: CameraDevice) {
                    device = cd
                    try {
                        val req = cd.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
                        req.addTarget(reader.surface)
                        @Suppress("DEPRECATION")
                        cd.createCaptureSession(listOf(reader.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(s: CameraCaptureSession) {
                                try { s.capture(req.build(), null, handler) } catch (_: Exception) { latch.countDown() }
                            }
                            override fun onConfigureFailed(s: CameraCaptureSession) { latch.countDown() }
                        }, handler)
                    } catch (_: Exception) { latch.countDown() }
                }
                override fun onDisconnected(cd: CameraDevice) { try { cd.close() } catch (_: Exception) {}; latch.countDown() }
                override fun onError(cd: CameraDevice, error: Int) { try { cd.close() } catch (_: Exception) {}; latch.countDown() }
            }, handler)
            latch.await(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
        } finally {
            try { device?.close() } catch (_: Exception) {}
            try { reader.close() } catch (_: Exception) {}
            thread.quitSafely()
        }
        return result
    }
}
