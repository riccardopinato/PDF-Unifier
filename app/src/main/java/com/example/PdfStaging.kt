package com.example

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

internal const val MAX_PDF_BYTES = 256L * 1024 * 1024

internal suspend fun copyPdfBytes(input: InputStream, output: OutputStream): Long {
    val buffer = ByteArray(64 * 1024)
    var total = 0L
    while (true) {
        currentCoroutineContext().ensureActive()
        val count = input.read(buffer)
        if (count < 0) break
        total += count
        if (total > MAX_PDF_BYTES) throw IOException("PDF exceeds 256 MiB")
        output.write(buffer, 0, count)
    }
    if (total == 0L) throw IOException("Empty PDF")
    return total
}

internal suspend fun stagePdf(context: Context, uri: Uri): File {
    var staged: File? = null
    try {
        return withContext(Dispatchers.IO) {
            val file = File.createTempFile("pdf_stage_", ".pdf", context.cacheDir)
            staged = file
            (context.contentResolver.openInputStream(uri)
                ?: throw IOException(context.getString(R.string.pdf_input_unreadable))).use { input ->
                file.outputStream().use { output -> copyPdfBytes(input, output) }
            }
            file
        }
    } catch (failure: Throwable) {
        withContext(NonCancellable + Dispatchers.IO) { staged?.delete() }
        throw failure
    }
}
