package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfReadingPositionStoreTest {
    @Test
    fun restoresLatestPositionFromNewStoreAndIsolatesDocuments() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val root = File(app.cacheDir, "reading_test_${UUID.randomUUID()}")
        try {
            val first = Uri.parse("content://test/document/first")
            val second = Uri.parse("content://test/document/second")
            val store = PdfReadingPositionStore(root)
            assertNull(store.load(first))
            store.save(first, PdfReadingPosition(2, 10))
            store.save(first, PdfReadingPosition(7, 10))
            assertEquals(PdfReadingPosition(7, 10), PdfReadingPositionStore(root).load(first))
            assertNull(store.load(second))
            store.save(first, PdfReadingPosition(0, 10))
            assertEquals(PdfReadingPosition(0, 10), PdfReadingPositionStore(root).load(first))
        } finally {
            root.deleteRecursively()
        }
    }
}
