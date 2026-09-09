package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal class PdfReaderViewModel(context: Context, private val initialPage: Int?) : ViewModel() {
    private val app = context.applicationContext
    private val store = PdfRenderStore(app)
    private val positions = PdfReadingPositionStore(File(app.filesDir, "pdf_reading_positions"))
    val search = PdfSearchController(app, viewModelScope)
    val bookmarks = PdfBookmarkController(app, viewModelScope)
    val page = mutableIntStateOf(initialPage ?: 0)
    val count = mutableIntStateOf(0)
    val error = mutableStateOf<String?>(null)
    val positionWarning = mutableStateOf(false)
    private var started = false
    private var source: Uri? = null
    private var lastRequested: PdfReadingPosition? = null
    private var saveVersion = 0L

    fun load(uri: Uri) {
        if (started) return
        started = true
        source = uri
        search.bind(uri)
        viewModelScope.launch {
            try {
                val total = store.open(uri)
                val saved = if (initialPage != null) null else try {
                    positions.load(uri)
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { positionWarning.value = true; null }
                page.intValue = restoredPdfPage(total, initialPage, saved)
                count.intValue = total
                bookmarks.bind(uri, total)
                viewModelScope.launch {
                    snapshotFlow { page.intValue }.collectLatest {
                        delay(500)
                        persistPosition()
                    }
                }
                awaitCancellation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error.value = app.getString(R.string.reader_cannot_open)
            } finally {
                withContext(NonCancellable) {
                    try { store.close() } catch (_: Exception) { }
                }
            }
        }
    }

    // Called on main by the page observer and Activity.onStop.
    fun persistPosition() {
        val uri = source ?: return
        val total = count.intValue
        if (total <= 0 || error.value != null) return
        val position = PdfReadingPosition(page.intValue.coerceIn(0, total - 1), total)
        if (position == lastRequested) return
        lastRequested = position
        val version = ++saveVersion
        PdfReadingPositionWriter.save(app, uri, position) { success ->
            if (!success && version == saveVersion) {
                lastRequested = null
                positionWarning.value = true
            }
        }
    }

    suspend fun render(index: Int, edge: Int): Bitmap = store.render(index, edge)

    companion object {
        fun factory(context: Context, initialPage: Int?): ViewModelProvider.Factory {
            val app = context.applicationContext
            return object : ViewModelProvider.Factory {
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(PdfReaderViewModel::class.java))
                    @Suppress("UNCHECKED_CAST")
                    return PdfReaderViewModel(app, initialPage) as T
                }
            }
        }
    }
}
