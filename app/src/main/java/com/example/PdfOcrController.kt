package com.example

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal data class PdfOcrUi(
    val visible: Boolean = false,
    val page: Int = 0,
    val running: Boolean = false,
    val text: String? = null,
    val failed: Boolean = false,
    val tooLong: Boolean = false
)

internal class PdfOcrController(
    private val scope: CoroutineScope,
    private val recognizePage: suspend (Int) -> String
) {
    private val mutex = Mutex()
    private var version = 0L
    private var job: Job? = null
    var ui by mutableStateOf(PdfOcrUi())
        private set

    fun start(page: Int) {
        require(page >= 0)
        if (ui.visible && ui.running) return
        job?.cancel()
        val request = ++version
        ui = PdfOcrUi(visible = true, page = page, running = true)
        job = scope.launch {
            try {
                val text = mutex.withLock { recognizePage(page) }
                currentCoroutineContext().ensureActive()
                if (request == version) ui = ui.copy(running = false, text = text)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (request == version) ui = ui.copy(running = false, failed = true,
                    tooLong = failure is PdfOcrTextTooLong)
            }
        }
    }

    fun dismiss() {
        version++
        job?.cancel()
        job = null
        // OCR text is kept only while its sheet is open, including configuration changes.
        ui = PdfOcrUi()
    }
}
