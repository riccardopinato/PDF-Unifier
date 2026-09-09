package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File

internal data class PdfBookmarkUi(
    val pages: List<Int> = emptyList(), val busy: Boolean = false,
    val loaded: Boolean = false, val errorResource: Int? = null
)

internal class PdfBookmarkController(context: Context, private val scope: CoroutineScope) {
    private val store = PdfBookmarkStore(File(context.applicationContext.filesDir, "pdf_bookmarks"))
    private var source: Uri? = null
    private var pageCount = 0
    var ui by mutableStateOf(PdfBookmarkUi())
        private set

    fun bind(uri: Uri, count: Int) {
        check(source == null || source == uri)
        source = uri
        pageCount = count
        refresh()
    }

    fun refresh() {
        val uri = source ?: return
        if (ui.busy) return
        ui = ui.copy(busy = true, errorResource = null)
        scope.launch {
            try {
                ui = PdfBookmarkUi(pages = store.load(uri, pageCount), loaded = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { ui = ui.copy(busy = false, errorResource = R.string.bookmark_error) }
        }
    }

    fun set(page: Int, bookmarked: Boolean) {
        val uri = source ?: return
        if (ui.busy || !ui.loaded || page !in 0 until pageCount) return
        ui = ui.copy(busy = true, errorResource = null)
        scope.launch {
            try {
                ui = PdfBookmarkUi(pages = store.set(uri, pageCount, page, bookmarked), loaded = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) {
                ui = ui.copy(busy = false, errorResource = if (failure is PdfBookmarkLimit)
                    R.string.bookmark_limit else R.string.bookmark_error)
            }
        }
    }
}
