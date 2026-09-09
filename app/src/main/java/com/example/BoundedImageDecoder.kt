package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

internal fun hasAnimation(file: File, mime: String): Boolean {
    if (mime != "image/png" && mime != "image/webp") return false
    RandomAccessFile(file, "r").use { input ->
        input.seek(if (mime == "image/png") 8L else 12L)
        while (input.filePointer + 8 <= input.length()) {
            val size: Long
            val type: Int
            if (mime == "image/png") {
                size = input.readInt().toLong() and 0xffffffffL
                type = input.readInt()
                if (type == 0x6163544c) return true // acTL
            } else {
                type = input.readInt()
                size = Integer.reverseBytes(input.readInt()).toLong() and 0xffffffffL
                if (type == 0x414e494d || type == 0x414e4d46) return true // ANIM / ANMF
            }
            val next = input.filePointer + size + if (mime == "image/png") 4L else size % 2
            if (next > input.length()) throw IOException("Invalid image container")
            input.seek(next)
        }
    }
    return false
}

internal fun imageSampleSize(width: Int, height: Int, maxEdge: Int): Int {
    require(width > 0 && height > 0 && maxEdge > 0)
    var sample = 1
    while ((width.toLong() + sample - 1) / sample > maxEdge ||
        (height.toLong() + sample - 1) / sample > maxEdge) {
        // Real decoder dimensions fit Int; reject impossible inputs instead of overflowing.
        require(sample <= Int.MAX_VALUE / 2)
        sample *= 2
    }
    return sample
}

internal fun exifMatrix(orientation: Int): Matrix = Matrix().apply {
    when (orientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> { setRotate(90f); postScale(-1f, 1f) }
        ExifInterface.ORIENTATION_ROTATE_90 -> setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> { setRotate(90f); postScale(1f, -1f) }
        ExifInterface.ORIENTATION_ROTATE_270 -> setRotate(270f)
    }
}

/** Must run on IO. The caller owns and must recycle the returned software bitmap. */
internal suspend fun decodePdfImage(context: Context, uri: Uri, maxEdge: Int): Bitmap {
    val cached = File.createTempFile("pdf_image_", ".bin", context.cacheDir)
    try {
        val maxInputBytes = 50L * 1024 * 1024
        (context.contentResolver.openInputStream(uri)
            ?: throw IOException(context.getString(R.string.pdf_input_unreadable))).use { input ->
            cached.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    if (total > maxInputBytes) throw IOException(context.getString(R.string.pdf_image_too_large))
                    output.write(buffer, 0, count)
                }
            }
        }
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(cached.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException(context.getString(R.string.pdf_image_invalid))
        }
        // Animated/multi-frame formats need explicit page-selection UX; never silently take frame one.
        if (bounds.outMimeType !in setOf("image/jpeg", "image/png", "image/webp")) {
            throw IOException(context.getString(R.string.pdf_image_invalid))
        }
        if (hasAnimation(cached, bounds.outMimeType.orEmpty())) {
            throw IOException(context.getString(R.string.pdf_image_invalid))
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = imageSampleSize(bounds.outWidth, bounds.outHeight, maxEdge)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        }
        currentCoroutineContext().ensureActive()
        val bitmap = BitmapFactory.decodeFile(cached.absolutePath, options)
            ?: throw IOException(context.getString(R.string.pdf_image_invalid))
        try {
            if (bitmap.width > maxEdge || bitmap.height > maxEdge) {
                throw IOException(context.getString(R.string.pdf_image_too_large))
            }
            val orientation = try {
                ExifInterface(cached.absolutePath).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } catch (_: IOException) {
                ExifInterface.ORIENTATION_NORMAL
            }
            currentCoroutineContext().ensureActive()
            val matrix = exifMatrix(orientation)
            if (matrix.isIdentity) return bitmap
            val oriented = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (oriented !== bitmap) bitmap.recycle()
            return oriented
        } catch (failure: Throwable) {
            // Clean up and rethrow, including cancellation and VM errors; no fake success.
            bitmap.recycle()
            throw failure
        }
    } finally {
        cached.delete()
    }
}
