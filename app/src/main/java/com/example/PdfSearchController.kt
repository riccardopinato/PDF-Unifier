package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PdfSearchUi(
    val query: String = "", val running: Boolean = false,
    val result: PdfSearchResult? = null, val errorResource: Int? = null,
    val selected: Int = -1
)

// Owned by PdfReaderViewModel; all state mutations occur on the main thread.
internal class PdfSearchController(context: Context, private val scope: CoroutineScope) {
    private val app = context.applicationContext
    private val mutex = Mutex()
    private var source: Uri? = null
    private var job: Job? = null
    private var version = 0L
    var ui by mutableStateOf(PdfSearchUi())
        private set

    fun bind(uri: Uri) { source = uri }

    fun edit(query: String) {
        cancel()
        ui = PdfSearchUi(query = query.take(120))
    }

    fun cancel() {
        version++
        job?.cancel()
        job = null
        ui = ui.copy(running = false)
    }

    fun submit() {
        val uri = source ?: return
        val term = ui.query.trim()
        if (term.isEmpty() || ui.running) return
        cancel()
        val requestVersion = version
        ui = PdfSearchUi(query = ui.query, running = true)
        job = scope.launch {
            try {
                // Wait for a cancelled PDFBox operation to release its document first.
                val result = mutex.withLock { searchPdfText(app, uri, term) }
                if (requestVersion == version) ui = ui.copy(running = false, result = result)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                if (requestVersion == version) ui = ui.copy(running = false,
                    errorResource = if (failure is PdfSearchPageTooComplex)
                        R.string.search_too_complex else R.string.search_error)
            }
        }
    }

    fun move(delta: Int): Int? {
        val hits = ui.result?.hits ?: return null
        if (hits.isEmpty()) return null
        val next = (ui.selected + delta).coerceIn(0, hits.lastIndex)
        ui = ui.copy(selected = next)
        return hits[next].page
    }
}
