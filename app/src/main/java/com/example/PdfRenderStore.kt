package com.example

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.sqrt

internal fun renderDimensions(width: Int, height: Int, edge: Int): Pair<Int, Int> {
    require(width > 0 && height > 0 && edge in 64..2000)
    val scale = minOf(edge.toDouble() / max(width, height),
        sqrt(2_000_000.0 / (width.toDouble() * height)))
    return max(1, (width * scale).toInt()) to max(1, (height * scale).toInt())
}

internal class PdfRenderStore(context: Context) {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private var source: File? = null
    private var renderer: PdfRenderer? = null
    private var closed = false
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
        // Do not recycle evicted bitmaps: Compose may still be displaying them.
    }

    suspend fun open(uri: Uri): Int = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(!closed && renderer == null)
            val file = stagePdf(app, uri)
            source = file
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                renderer = PdfRenderer(descriptor) // The renderer owns this descriptor after success.
            } catch (failure: Throwable) {
                descriptor.close()
                throw failure
            }
            val count = checkNotNull(renderer).pageCount
            require(count > 0) { app.getString(R.string.reader_empty) }
            count
        }
    }

    suspend fun render(index: Int, edge: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            currentCoroutineContext().ensureActive()
            check(!closed)
            val key = "$index:$edge"
            cache.get(key)?.let { return@withLock it }
            val active = checkNotNull(renderer)
            require(index in 0 until active.pageCount)
            active.openPage(index).use { page ->
                val (width, height) = renderDimensions(page.width, page.height, edge)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                try {
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    currentCoroutineContext().ensureActive()
                    cache.put(key, bitmap)
                    bitmap
                } catch (failure: Throwable) {
                    bitmap.recycle()
                    throw failure
                }
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            closed = true
            try { renderer?.close() }
            finally {
                renderer = null
                source?.delete()
                source = null
                cache.evictAll()
            }
        }
    }
}
