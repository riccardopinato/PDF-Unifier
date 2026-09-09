package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
class PdfImportTest {
    private val engine = object : PdfEngine {
        override suspend fun generate(request: PdfRequest): Pair<Uri, String> = error("Not expected")
        override suspend fun pageCount(uri: Uri) = 5
        override suspend fun extractText(uriOrPath: String) = ""
    }
    private val history = object : PdfHistory {
        override suspend fun load() = emptyList<HistoryItem>()
        override suspend fun add(name: String, uri: String, type: String) {}
        override suspend fun delete(id: Int) {}
        override suspend fun clear() {}
    }

    @Test fun failedImportDoesNotReplaceTheExistingSelection() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val reader = object : PdfInputReader {
                override suspend fun read(uris: List<Uri>): List<SelectedPdfItem> = throw IOException("Read failed")
            }
            val model = PdfViewModel(ApplicationProvider.getApplicationContext<Context>(), engine, history, reader)
            val old = SelectedPdfItem(uri = Uri.parse("content://test/old"), name = "old.pdf", sizeString = "1 KB")
            model.fileList.add(old)
            model.importFiles(listOf(Uri.parse("content://test/new")), AppMode.SPLIT_PDF, true)
            advanceUntilIdle()
            assertEquals(listOf(old), model.fileList.toList())
            assertNotNull(model.errorMessage.value)
            assertFalse(model.isProcessing.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun duplicateUrisAreNotAddedTwice() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val reader = object : PdfInputReader {
                override suspend fun read(uris: List<Uri>) = uris.map {
                    SelectedPdfItem(uri = it, name = "input.pdf", sizeString = "1 KB")
                }
            }
            val model = PdfViewModel(ApplicationProvider.getApplicationContext<Context>(), engine, history, reader)
            val uri = Uri.parse("content://test/same")
            model.importFiles(listOf(uri, uri), AppMode.MERGE_PDF, false)
            advanceUntilIdle()
            model.importFiles(listOf(uri), AppMode.MERGE_PDF, false)
            advanceUntilIdle()
            assertEquals(1, model.fileList.size)
        } finally { Dispatchers.resetMain() }
    }
}
