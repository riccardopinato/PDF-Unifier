package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal data class PdfOcrExportNotice(val id: Long, val resource: Int)

internal class PdfOcrExportController(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val snapshots = PdfOcrTextExportStore(File(app.cacheDir, "ocr_text_exports"))
    private var sequence = 0L
    var busy by mutableStateOf(false)
        private set
    var notice by mutableStateOf<PdfOcrExportNotice?>(null)
        private set

    suspend fun prepare(text: String): String = snapshots.prepare(text)
    suspend fun discard(token: String) = snapshots.discard(token)

    fun reportFailure(resource: Int = R.string.ocr_export_failed) {
        notice = PdfOcrExportNotice(++sequence, resource)
    }
    fun clearNotice(id: Long) { if (notice?.id == id) notice = null }

    fun finish(token: String?, destination: Uri?) {
        if (busy) return
        busy = true
        // Start immediately; finish the small write and cleanup even if the reader closes.
        // Process termination can still interrupt the write.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable) {
                try {
                    if (destination != null) {
                        val bytes = snapshots.read(requireNotNull(token))
                        writeOcrTextDocument(app, destination, bytes)
                        notice = PdfOcrExportNotice(++sequence, R.string.ocr_export_saved)
                    }
                } catch (_: Exception) {
                    reportFailure()
                } finally {
                    if (token != null) snapshots.discard(token)
                    busy = false
                }
            }
        }
    }
}
