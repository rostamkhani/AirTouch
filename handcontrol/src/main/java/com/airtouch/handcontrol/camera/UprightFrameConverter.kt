package com.airtouch.handcontrol.camera

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Converts an RGBA_8888 [ImageProxy] into a bitmap that is upright for the current screen
 * rotation and, for the front camera, mirrored like the preview. Landmarks found on the result
 * therefore line up with what the user sees, in any phone orientation.
 *
 * Reuses its bitmaps; the returned bitmap is only valid until the next [convert] call.
 */
class UprightFrameConverter {
    private var source: Bitmap? = null
    private var target: Bitmap? = null
    private var packed: ByteBuffer? = null
    private var row: ByteArray? = null
    private val matrix = Matrix()
    private val canvas = Canvas()
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    fun convert(image: ImageProxy, mirror: Boolean): Bitmap {
        val src = copyPixels(image)
        return transform(src, image.imageInfo.rotationDegrees, mirror)
    }

    /** Rotates [src] clockwise by [rotationDegrees] and optionally mirrors it horizontally. */
    fun transform(src: Bitmap, rotationDegrees: Int, mirror: Boolean): Bitmap {
        val rotation = ((rotationDegrees % 360) + 360) % 360
        val swap = rotation == 90 || rotation == 270
        val outWidth = if (swap) src.height else src.width
        val outHeight = if (swap) src.width else src.height
        val out = target?.takeIf { it.width == outWidth && it.height == outHeight }
            ?: Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888).also { target = it }
        matrix.reset()
        matrix.postTranslate(-src.width / 2f, -src.height / 2f)
        matrix.postRotate(rotation.toFloat())
        if (mirror) matrix.postScale(-1f, 1f)
        matrix.postTranslate(outWidth / 2f, outHeight / 2f)
        canvas.setBitmap(out)
        canvas.drawBitmap(src, matrix, paint)
        canvas.setBitmap(null)
        return out
    }

    private fun copyPixels(image: ImageProxy): Bitmap {
        val width = image.width
        val height = image.height
        val bitmap = source?.takeIf { it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { source = it }
        val plane = image.planes[0]
        val buffer = plane.buffer
        buffer.rewind()
        val rowBytes = width * 4
        if (plane.pixelStride == 4 && plane.rowStride == rowBytes) {
            bitmap.copyPixelsFromBuffer(buffer)
            return bitmap
        }
        val out = packed?.takeIf { it.capacity() == rowBytes * height }
            ?: ByteBuffer.allocateDirect(rowBytes * height).also { packed = it }
        val line = row?.takeIf { it.size == rowBytes } ?: ByteArray(rowBytes).also { row = it }
        out.clear()
        for (y in 0 until height) {
            buffer.position(y * plane.rowStride)
            buffer.get(line, 0, rowBytes)
            out.put(line)
        }
        out.rewind()
        bitmap.copyPixelsFromBuffer(out)
        return bitmap
    }
}
