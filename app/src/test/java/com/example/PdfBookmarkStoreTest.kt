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
class PdfBookmarkStoreTest {
    private fun directory(): File {
        val app = ApplicationProvider.getApplicationContext<Context>()
        return File(app.cacheDir, "bookmark_test_${UUID.randomUUID()}").also { check(it.mkdirs()) }
    }

    @Test
    fun bookmarksSurviveNewStoreAndAreIsolatedPerDocument() = runBlocking {
        val root = directory()
        try {
            val first = Uri.parse("content://test/document/one")
            val second = Uri.parse("content://test/document/two")
            val store = PdfBookmarkStore(root)
            store.set(first, 10, 7, true)
            store.set(first, 10, 2, true)
            store.set(first, 10, 2, true)
            val reopened = PdfBookmarkStore(root)
            assertEquals(listOf(2, 7), reopened.load(first, 10))
            assertTrue(reopened.load(second, 10).isEmpty())
            assertEquals(listOf(7), reopened.set(first, 10, 2, false))
            assertEquals(listOf(7), PdfBookmarkStore(root).load(first, 10))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun pagesOutsideCurrentDocumentAreNotOfferedForNavigation() = runBlocking {
        val root = directory()
        try {
            val uri = Uri.parse("content://test/document/shortened")
            val store = PdfBookmarkStore(root)
            store.set(uri, 10, 2, true)
            store.set(uri, 10, 8, true)
            assertEquals(listOf(2), store.load(uri, 4))
        } finally {
            root.deleteRecursively()
        }
    }
}
