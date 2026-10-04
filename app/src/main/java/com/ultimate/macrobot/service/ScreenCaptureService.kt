// SPDX-License-Identifier: GPL-3.0-only
// Copyright (C) 2026 Ultrevo. See LICENSE and NOTICE.

package com.ultimate.macrobot.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Display
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/** Foreground service that mirrors the screen into an ImageReader so we can grab screenshots. */
class ScreenCaptureService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var width = 0
    private var height = 0
    private var dpi = 0
    private var lastFrame: Bitmap? = null

    val isReady: Boolean get() = projection != null && display != null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle("Ultrebo")
            .setContentText("Screen capture is on for image recognition")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        ServiceCompat.startForeground(
            this, NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )

        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data == null || projection != null) {
            if (projection == null) stopSelf()
            return START_NOT_STICKY
        }
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mpm.getMediaProjection(code, data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                release()
                stopSelf()
            }
        }, handler)
        dpi = resources.displayMetrics.densityDpi
        buildDisplay(realSize())
        instance = this
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun realSize(): Point {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        return Point().also { dm.getDisplay(Display.DEFAULT_DISPLAY).getRealSize(it) }
    }

    private fun buildDisplay(size: Point) {
        width = size.x
        height = size.y
        reader?.close()
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        val surface = reader!!.surface
        val existing = display
        if (existing == null) {
            display = projection?.createVirtualDisplay(
                "macrobot", width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, handler,
            )
        } else {
            existing.resize(width, height, dpi)
            existing.surface = surface
        }
    }

    /**
     * Latest screenshot at full screen resolution. The screen only produces frames when it
     * changes, so this returns the previous frame when nothing new has arrived.
     */
    @Synchronized
    fun capture(): Bitmap? {
        if (!isReady) return null
        val size = realSize()
        if (size.x != width || size.y != height) {
            buildDisplay(size) // rotated
            lastFrame = null
        }
        readLatest()?.let { lastFrame = it }
        return lastFrame
    }

    /** Waits for a frame produced after this call (used after hiding our own overlays). */
    fun captureFresh(timeoutMs: Long = 1500): Bitmap? {
        synchronized(this) { readLatest() } // drop anything queued earlier
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val frame = synchronized(this) { readLatest() }
            if (frame != null) {
                synchronized(this) { lastFrame = frame }
                return frame
            }
            Thread.sleep(50)
        }
        return capture()
    }

    private fun readLatest(): Bitmap? {
        val image = reader?.acquireLatestImage() ?: return null
        try {
            val plane = image.planes[0]
            val rowPadding = plane.rowStride - plane.pixelStride * image.width
            val padded = Bitmap.createBitmap(
                image.width + rowPadding / plane.pixelStride, image.height, Bitmap.Config.ARGB_8888,
            )
            padded.copyPixelsFromBuffer(plane.buffer)
            return if (rowPadding == 0) padded else Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        } finally {
            image.close()
        }
    }

    private fun release() {
        instance = null
        display?.release()
        display = null
        reader?.close()
        reader = null
        projection?.stop()
        projection = null
    }

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Screen capture", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        @Volatile var instance: ScreenCaptureService? = null
        private const val CHANNEL = "capture"
        private const val NOTIF_ID = 1
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .putExtra(EXTRA_CODE, resultCode)
                .putExtra(EXTRA_DATA, data)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }
    }
}
