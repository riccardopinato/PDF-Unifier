package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.IOException

internal fun pdfCopyName(name: String): String {
    val clean = name.replace(Regex("[\\\\/\\p{Cntrl}]"), "_").trim().take(100)
    val base = if (clean.endsWith(".pdf", ignoreCase = true)) clean.dropLast(4) else clean
    return (base.ifBlank { "Document" }) + ".pdf"
}

/** destination must come from ACTION_CREATE_DOCUMENT, never from an arbitrary existing URI. */
internal suspend fun savePdfCopy(context: Context, source: Uri, destination: Uri, name: String): String =
    withContext(Dispatchers.IO) {
        require(source != destination) { "Source and destination must differ" }
        try {
            // Stage first: source read errors occur before opening the new destination for writing.
            val staged = stagePdf(context, source)
            try {
                val resolver = context.contentResolver
                val written = (resolver.openOutputStream(destination, "wt")
                    ?: throw IOException(context.getString(R.string.pdf_save_failed))).use { output ->
                    staged.inputStream().use { input -> copyPdfBytes(input, output) }
                }
                if (written != staged.length()) throw IOException(context.getString(R.string.pdf_save_failed))
                try {
                    resolver.takePersistableUriPermission(destination,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                } catch (_: SecurityException) { /* Provider may grant session-only access. */ }
                var actualName = pdfCopyName(name)
                try {
                    resolver.query(destination, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst() && !it.isNull(0)) actualName = it.getString(0)
                    }
                } catch (_: Exception) { /* Display name is optional; the copy is already complete. */ }
                actualName
            } finally { staged.delete() }
        } catch (failure: Exception) {
            // Only a newly created output is eligible for this cleanup.
            withContext(NonCancellable + Dispatchers.IO) {
                try { DocumentsContract.deleteDocument(context.contentResolver, destination) }
                catch (cleanup: Exception) { failure.addSuppressed(cleanup) }
            }
            throw failure
        }
    }
