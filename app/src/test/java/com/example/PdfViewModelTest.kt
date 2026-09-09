package com.example

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PdfViewModelTest {
    private class FakeEngine : PdfEngine {
        val gate = CompletableDeferred<Unit>()
        val requests = mutableListOf<PdfRequest>()
        override suspend fun generate(request: PdfRequest): Pair<Uri, String> {
            requests.add(request)
            gate.await()
            return Uri.parse("content://test/result") to "result.pdf"
        }
        override suspend fun pageCount(uri: Uri) = 3
        override suspend fun extractText(uriOrPath: String): String = throw IOException("Unreadable PDF")
    }
    private class FakeHistory(private val fail: Boolean = false) : PdfHistory {
        val types = mutableListOf<String>()
        override suspend fun load() = emptyList<HistoryItem>()
        override suspend fun add(name: String, uri: String, type: String) {
            if (fail) throw IOException("Database unavailable")
            types.add(type)
        }
        override suspend fun delete(id: Int) {}
        override suspend fun clear() {}
    }
    private fun seed(model: PdfViewModel) {
        model.fileList.add(SelectedPdfItem(uri = Uri.parse("content://test/a"), name = "a.pdf", sizeString = "1 KB"))
        model.fileList.add(SelectedPdfItem(uri = Uri.parse("content://test/b"), name = "b.pdf", sizeString = "1 KB"))
    }

    @Test fun requestKeepsOriginalInputsAndRejectsDoubleStart() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val app = ApplicationProvider.getApplicationContext<Context>()
            val engine = FakeEngine()
            val history = FakeHistory()
            val model = PdfViewModel(app, engine, history)
            seed(model)
            model.performAction(false)
            model.performAction(false)
            model.currentMode.value = AppMode.IMAGE_TO_PDF
            model.fileList.clear()
            runCurrent()
            assertEquals(1, engine.requests.size)
            assertEquals(PdfAction.MERGE, engine.requests.single().action)
            assertEquals(2, engine.requests.single().uris.size)
            engine.gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(listOf("MERGE"), history.types)
            assertFalse(model.isProcessing.value)
            assertNotNull(model.resultUri.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun historyFailureKeepsExportAndShareAvailable() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val engine = FakeEngine().apply { gate.complete(Unit) }
            val model = PdfViewModel(ApplicationProvider.getApplicationContext<Context>(), engine, FakeHistory(true))
            seed(model)
            model.performAction(true)
            advanceUntilIdle()
            assertNotNull(model.resultUri.value)
            assertEquals(model.resultUri.value, model.pendingShare.value)
            assertNotNull(model.historyWarning.value)
            assertNull(model.errorMessage.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun extractionFailureIsNotShownAsExtractedText() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val model = PdfViewModel(ApplicationProvider.getApplicationContext<Context>(), FakeEngine(), FakeHistory())
            model.extractText("content://test/broken")
            advanceUntilIdle()
            assertNotNull(model.errorMessage.value)
            assertFalse(model.showExtractedTextSheet.value)
            assertFalse(model.isProcessing.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun sameStoreRetainsSelectionAndRunningOperation() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val app = ApplicationProvider.getApplicationContext<Context>()
            val engine = FakeEngine()
            val factory = object : ViewModelProvider.Factory {
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                    @Suppress("UNCHECKED_CAST")
                    return PdfViewModel(app, engine, FakeHistory()) as T
                }
            }
            val first = ViewModelProvider(store, factory)[PdfViewModel::class.java]
            seed(first)
            first.performAction(false)
            runCurrent()
            val retained = ViewModelProvider(store, factory)[PdfViewModel::class.java]
            assertSame(first, retained)
            assertEquals(2, retained.fileList.size)
            assertTrue(retained.isProcessing.value)
            engine.gate.complete(Unit)
            advanceUntilIdle()
            assertNotNull(retained.resultUri.value)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}
