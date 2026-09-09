package com.example

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.UUID

internal fun ocrTextFileName(name: String, page: Int): String {
    require(page >= 0)
    val clean = name.replace(Regex("[\\\\/<>:\"|?*\\p{Cntrl}]"), "_").trim()
    val stem = if (clean.endsWith(".pdf", ignoreCase = true)) clean.dropLast(4) else clean
    val base = stem.take(80).trim(' ', '.').ifBlank { "Document" }
    return "${base}_p${page.toLong() + 1}_OCR.txt"
}

internal fun ocrTextUtf8(text: String): ByteArray {
    require(text.isNotBlank() && text.length <= 50_000)
    return text.toByteArray(Charsets.UTF_8)
}

internal class PdfOcrTextExportStore(private val directory: File) {
    private fun file(token: String): File {
        require(token.endsWith(".txt"))
        val id = UUID.fromString(token.removeSuffix(".txt"))
        require("$id.txt" == token)
        return File(directory, token)
    }

    suspend fun prepare(text: String): String {
        var staged: File? = null
        try {
            return withContext(Dispatchers.IO) {
                val bytes = ocrTextUtf8(text)
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException("Temporary folder unavailable")
                val token = "${UUID.randomUUID()}.txt"
                val target = file(token)
                staged = target
                target.outputStream().use { output -> output.write(bytes); output.fd.sync() }
                token
            }
        } catch (failure: Throwable) {
            withContext(NonCancellable + Dispatchers.IO) { staged?.delete() }
            throw failure
        }
    }

    suspend fun read(token: String): ByteArray = withContext(Dispatchers.IO) {
        file(token).inputStream().use { input ->
            val bytes = ByteArray(200_001)
            var size = 0
            while (size < bytes.size) {
                val count = input.read(bytes, size, bytes.size - size)
                if (count < 0) break
                size += count
            }
            if (size == 0 || size > 200_000) throw IOException("Invalid text snapshot")
            bytes.copyOf(size)
        }
    }

    suspend fun discard(token: String) = withContext(Dispatchers.IO) {
        // Only UUID-named temporary files in this dedicated folder are eligible.
        try { file(token).delete() } catch (_: Exception) { }
        Unit
    }
}

/** destination must be the URI just returned by CreateDocument("text/plain"). */
internal suspend fun writeOcrTextDocument(context: Context, destination: Uri, bytes: ByteArray) =
    withContext(Dispatchers.IO) {
        require(destination.scheme == "content" && !destination.authority.isNullOrBlank())
        require(bytes.isNotEmpty() && bytes.size <= 200_000)
        val output = context.contentResolver.openOutputStream(destination, "wt")
            ?: throw IOException("Unable to open text destination")
        output.use { it.write(bytes); it.flush() }
        // Do not delete external documents on failure: provider behavior can vary.
    }
