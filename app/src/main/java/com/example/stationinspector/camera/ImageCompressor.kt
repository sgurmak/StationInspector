package com.example.stationinspector.camera

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import com.example.stationinspector.domain.model.PhotoType
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageCompressor @Inject constructor() {

    companion object {
        private const val TAG = "ImageCompressor"
    }

    data class CompressionConfig(
        val maxWidth: Int,
        val maxHeight: Int,
        val startingQuality: Int,
        val maxSizeBytes: Long
    )

    fun compress(bitmap: Bitmap, type: PhotoType): ByteArray {
        try {
            val config = when (type) {
                // Normal Photos: 960 width x 1280 height, Max 500 KB
                PhotoType.CLIENT_REPORT -> CompressionConfig(960, 1280, 80, 500 * 1024L)
                // Defects: 1200 width x 1600 height, Max 800 KB (need slightly more detail)
                PhotoType.INTERNAL_DEFECT -> CompressionConfig(1200, 1600, 85, 800 * 1024L)
                // Default fallback
                else -> CompressionConfig(960, 1280, 80, 500 * 1024L)
            }

            // 1. Resize bitmap if it exceeds max dimensions, maintain aspect ratio
            val resizedBitmap = resizeBitmap(bitmap, config.maxWidth, config.maxHeight)

            // 2. Compress with quality adjustments
            var quality = config.startingQuality
            val stream = ByteArrayOutputStream()

            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            var byteArray = stream.toByteArray()

            // Loop to reduce quality if file is too large
            while (byteArray.size > config.maxSizeBytes && quality > 10) {
                stream.reset()
                quality -= 5
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
                byteArray = stream.toByteArray()
            }

            if (resizedBitmap != bitmap) {
                resizedBitmap.recycle()
            }

            Log.d(TAG, "Compressed size: ${byteArray.size / 1024} KB, final quality: $quality")

            return byteArray
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed gracefully. Falling back to 100 quality JPEG.", e)
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
            return stream.toByteArray()
        }
    }

    private fun resizeBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        val widthRatio = maxWidth.toFloat() / width.toFloat()
        val heightRatio = maxHeight.toFloat() / height.toFloat()
        val scale = minOf(widthRatio, heightRatio)

        val matrix = Matrix()
        matrix.postScale(scale, scale)

        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }
}
