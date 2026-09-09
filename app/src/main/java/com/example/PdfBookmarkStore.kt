package com.example

import android.net.Uri
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest

internal const val MAX_PDF_BOOKMARKS = 500
internal class PdfBookmarkLimit : IOException()

internal class PdfBookmarkStore(private val directory: File) {
    // Shared by all reader instances in this app process.
    companion object { private val mutex = Mutex() }

    private fun file(uri: Uri): AtomicFile {
        val digest = MessageDigest.getInstance("SHA-256").digest(uri.toString().toByteArray(Charsets.UTF_8))
        val key = digest.joinToString("") { (it.toInt() and 255).toString(16).padStart(2, '0') }
        return AtomicFile(File(directory, "$key.txt"))
    }

    private fun read(file: AtomicFile): Set<Int> {
        val input = try { file.openRead() } catch (failure: FileNotFoundException) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) throw failure
            return emptySet()
        }
        return input.bufferedReader(Charsets.UTF_8).use { reader ->
            if (reader.readLine() != "PDF_BOOKMARKS_1") throw IOException("Invalid bookmark format")
            val pages = mutableSetOf<Int>()
            var lines = 0
            while (true) {
                val line = reader.readLine() ?: break
                if (++lines > MAX_PDF_BOOKMARKS || line.length > 10) throw IOException("Invalid bookmark count")
                val page = line.toIntOrNull()?.takeIf { it >= 0 } ?: throw IOException("Invalid page")
                pages.add(page)
            }
            pages.toSet()
        }
    }

    suspend fun load(uri: Uri, pageCount: Int): List<Int> = withContext(Dispatchers.IO) {
        require(pageCount > 0)
        mutex.withLock { read(file(uri)).filter { it < pageCount }.sorted() }
    }

    suspend fun set(uri: Uri, pageCount: Int, page: Int, bookmarked: Boolean): List<Int> =
        withContext(Dispatchers.IO) {
            require(pageCount > 0 && page in 0 until pageCount)
            mutex.withLock {
                currentCoroutineContext().ensureActive()
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Bookmark folder unavailable")
                val target = file(uri)
                // Re-read under the lock to preserve changes from another reader.
                val updated = read(target).filter { it < pageCount }.toMutableSet()
                if (bookmarked) updated.add(page) else updated.remove(page)
                if (updated.size > MAX_PDF_BOOKMARKS) throw PdfBookmarkLimit()
                val sorted = updated.sorted()
                val bytes = ("PDF_BOOKMARKS_1\n" + sorted.joinToString("\n")).toByteArray(Charsets.UTF_8)
                val output = target.startWrite()
                try {
                    output.write(bytes)
                    output.fd.sync()
                    target.finishWrite(output)
                } catch (failure: Throwable) {
                    target.failWrite(output)
                    throw failure
                }
                if (read(target) != updated) throw IOException("Bookmark verification failed")
                sorted
            }
        }
}
