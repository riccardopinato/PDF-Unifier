package com.example

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.Locale

internal interface PdfInputReader {
    suspend fun read(uris: List<Uri>): List<SelectedPdfItem>
}

internal class AndroidPdfInputReader(context: Context) : PdfInputReader {
    private val app = context.applicationContext

    override suspend fun read(uris: List<Uri>): List<SelectedPdfItem> = withContext(Dispatchers.IO) {
        uris.distinct().map { uri ->
            currentCoroutineContext().ensureActive()
            val resolver = app.contentResolver
            // The picker grant is usable even if the provider does not support persistence.
            try {
                resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: SecurityException) {
                // Some providers grant access only for the current session.
            }
            (resolver.openInputStream(uri) ?: throw IOException(app.getString(R.string.pdf_input_unreadable))).use {
                if (it.read() == -1) throw IOException(app.getString(R.string.pdf_input_empty))
            }
            var name = app.getString(R.string.default_doc_name)
            var size = app.getString(R.string.unknown_size)
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                    if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) {
                        val bytes = cursor.getLong(sizeIndex)
                        size = when {
                            bytes < 0 -> size
                            bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / 1048576.0)
                            bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024.0)
                            else -> "$bytes B"
                        }
                    }
                }
            }
            SelectedPdfItem(uri = uri, name = name, sizeString = size)
        }
    }
}
