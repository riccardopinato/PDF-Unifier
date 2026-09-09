package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PdfBookmarkDeviceTest {
    private fun directory(): File {
        val app = ApplicationProvider.getApplicationContext<Context>()
        return File(app.cacheDir, "bookmark_test_${UUID.randomUUID()}").also { check(it.mkdirs()) }
    }

    @Test fun bookmarksSurviveNewStoreAndAreIsolatedPerDocument() = runBlocking {
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
        } finally { root.deleteRecursively() }
    }

    @Test fun concurrentStoresPreserveBothChanges() = runBlocking {
        val root = directory()
        try {
            val uri = Uri.parse("content://test/document/concurrent")
            listOf(
                async { PdfBookmarkStore(root).set(uri, 10, 1, true) },
                async { PdfBookmarkStore(root).set(uri, 10, 8, true) }
            ).awaitAll()
            assertEquals(listOf(1, 8), PdfBookmarkStore(root).load(uri, 10))
        } finally { root.deleteRecursively() }
    }

    @Test fun pagesOutsideCurrentDocumentAreNotOfferedForNavigation() = runBlocking {
        val root = directory()
        try {
            val uri = Uri.parse("content://test/document/shortened")
            val store = PdfBookmarkStore(root)
            store.set(uri, 10, 2, true)
            store.set(uri, 10, 8, true)
            assertEquals(listOf(2), store.load(uri, 4))
        } finally { root.deleteRecursively() }
    }

    @Test fun exceedingLimitPreservesSavedBookmarks() = runBlocking {
        val root = directory()
        try {
            val uri = Uri.parse("content://test/document/limit")
            val store = PdfBookmarkStore(root)
            store.set(uri, 600, 0, true)
            // Seed the documented storage format without 500 individual disk syncs.
            root.listFiles()!!.single { it.extension == "txt" }.writeText(
                "PDF_BOOKMARKS_1\n" + (0 until MAX_PDF_BOOKMARKS).joinToString("\n"))
            try {
                store.set(uri, 600, 550, true)
                fail("Expected bookmark limit")
            } catch (_: PdfBookmarkLimit) { }
            assertEquals((0 until MAX_PDF_BOOKMARKS).toList(), store.load(uri, 600))
        } finally { root.deleteRecursively() }
    }
}
