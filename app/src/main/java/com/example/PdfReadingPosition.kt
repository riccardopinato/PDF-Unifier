package com.example

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest

internal data class PdfReadingPosition(val page: Int, val pageCount: Int)

internal fun restoredPdfPage(total: Int, sessionPage: Int?, saved: PdfReadingPosition?): Int {
    require(total > 0)
    if (sessionPage != null) return sessionPage.coerceIn(0, total - 1)
    return saved?.takeIf { it.pageCount == total && it.page in 0 until total }?.page ?: 0
}

internal class PdfReadingPositionStore(private val directory: File) {
    companion object { private val mutex = Mutex() }

    private fun file(uri: Uri): AtomicFile {
        val hash = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return AtomicFile(File(directory, "$hash.txt"))
    }

    private fun read(target: AtomicFile): PdfReadingPosition? {
        val stream = try { target.openRead() } catch (failure: FileNotFoundException) {
            if (target.baseFile.exists() || File(target.baseFile.path + ".bak").exists()) throw failure
            return null
        }
        return stream.use {
            val bytes = ByteArray(128)
            var size = 0
            while (size < bytes.size) {
                val count = it.read(bytes, size, bytes.size - size)
                if (count < 0) break
                size += count
            }
            if (size == bytes.size) throw IOException("Invalid reading position")
            val parts = String(bytes, 0, size, Charsets.UTF_8).split('\n')
            if (parts.size != 3 || parts[0] != "PDF_READING_1") throw IOException("Invalid reading position")
            val page = parts[1].toIntOrNull() ?: throw IOException("Invalid page")
            val total = parts[2].toIntOrNull() ?: throw IOException("Invalid page count")
            if (total <= 0 || page !in 0 until total) throw IOException("Invalid reading range")
            PdfReadingPosition(page, total)
        }
    }

    // Acquire the shared lock before switching dispatcher, preserving queued read/write order.
    suspend fun load(uri: Uri): PdfReadingPosition? = mutex.withLock {
        withContext(Dispatchers.IO) { read(file(uri)) }
    }

    suspend fun save(uri: Uri, position: PdfReadingPosition) = mutex.withLock {
        withContext(Dispatchers.IO) {
            require(position.pageCount > 0 && position.page in 0 until position.pageCount)
            if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Position folder unavailable")
            val target = file(uri)
            val output = target.startWrite()
            try {
                output.write("PDF_READING_1\n${position.page}\n${position.pageCount}".toByteArray(Charsets.UTF_8))
                output.fd.sync()
                target.finishWrite(output)
            } catch (failure: Throwable) {
                target.failWrite(output)
                throw failure
            }
            if (read(target) != position) throw IOException("Position verification failed")
        }
    }
}

// Small pending writes may finish after a reader ViewModel is cleared.
// This does not guarantee completion if Android terminates the whole process.
internal object PdfReadingPositionWriter {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun save(context: Context, uri: Uri, position: PdfReadingPosition, onComplete: (Boolean) -> Unit) {
        val directory = File(context.applicationContext.filesDir, "pdf_reading_positions")
        scope.launch {
            val success = try {
                PdfReadingPositionStore(directory).save(uri, position)
                true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { false }
            onComplete(success)
        }
    }
}
