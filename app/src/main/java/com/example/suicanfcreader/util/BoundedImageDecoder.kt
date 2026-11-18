package com.example.suicanfcreader.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.InputStream

object BoundedImageDecoder {
    private const val DEFAULT_MAX_DIMENSION = 2048
    private const val MAX_DIMENSION = 4096
    private const val MAX_BYTES = 20 * 1024 * 1024
    private const val BUFFER_SIZE = 8 * 1024

    fun decode(context: Context, uri: Uri, maxDimension: Int = DEFAULT_MAX_DIMENSION): Bitmap? {
        val dimensionLimit = maxDimension.coerceIn(64, MAX_DIMENSION)
        val pixelLimit = dimensionLimit.toLong() * dimensionLimit.toLong()
        return runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBounded(MAX_BYTES) }
                ?: return@runCatching null
            if (bytes.isEmpty()) return@runCatching null

            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            if (bounds.outMimeType?.startsWith("image/") == false) return@runCatching null

            val sampleSize = calculateInSampleSize(
                width = bounds.outWidth,
                height = bounds.outHeight,
                maxDimension = dimensionLimit,
                maxPixels = pixelLimit
            )
            val bitmap = BitmapFactory.decodeByteArray(
                bytes,
                0,
                bytes.size,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            ) ?: return@runCatching null
            if (bitmap.width.toLong() * bitmap.height > pixelLimit) {
                bitmap.recycle()
                return@runCatching null
            }
            bitmap
        }.getOrNull()
    }

    private fun calculateInSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int,
        maxPixels: Long
    ): Int {
        var sampleSize = 1
        while (width / sampleSize > maxDimension ||
            height / sampleSize > maxDimension ||
            (width / sampleSize).toLong() * (height / sampleSize) > maxPixels
        ) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray? {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(BUFFER_SIZE)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            total += read
            if (total > maxBytes) return null
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }
}
