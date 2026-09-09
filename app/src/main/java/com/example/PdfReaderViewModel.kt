package com.example

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class PdfReaderViewModel(context: Context, initialPage: Int) : ViewModel() {
    private val app = context.applicationContext
    private val store = PdfRenderStore(app)
    val search = PdfSearchController(app, viewModelScope)
    val bookmarks = PdfBookmarkController(app, viewModelScope)
    val page = mutableIntStateOf(initialPage)
    val count = mutableIntStateOf(0)
    val error = mutableStateOf<String?>(null)
    private var started = false

    fun load(uri: Uri) {
        if (started) return
        started = true
        search.bind(uri)
        viewModelScope.launch {
            try {
                val total = store.open(uri)
                page.intValue = page.intValue.coerceIn(0, total - 1)
                count.intValue = total
                bookmarks.bind(uri, total)
                // Own the store for the ViewModel lifetime, including configuration changes.
                awaitCancellation()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error.value = app.getString(R.string.reader_cannot_open)
            } finally {
                withContext(NonCancellable) {
                    try { store.close() } catch (_: Exception) { }
                }
            }
        }
    }

    suspend fun render(index: Int, edge: Int): Bitmap = store.render(index, edge)

    companion object {
        fun factory(context: Context, initialPage: Int): ViewModelProvider.Factory {
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
