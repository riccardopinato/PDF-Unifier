package com.example

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfSearchDeviceTest {
    private fun createPdf(context: Context, texts: List<String?>): File {
        PDFBoxResourceLoader.init(context)
        val file = File.createTempFile("search_test_", ".pdf", context.cacheDir)
        try {
            PDDocument().use { document ->
                texts.forEach { text ->
                    val page = PDPage()
                    document.addPage(page)
                    if (text != null) PDPageContentStream(document, page).use { stream ->
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA, 12f)
                        stream.newLineAtOffset(40f, 700f)
                        stream.showText(text)
                        stream.endText()
                    }
                }
                document.save(file)
            }
            return file
        } catch (failure: Throwable) { file.delete(); throw failure }
    }

    @Test fun countsMatchesAndKeepsPageNumbersAcrossBlankPages() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val file = createPdf(app, listOf("PDF pdf", null, "test PDF"))
        try {
            val result = searchPdfText(app, Uri.fromFile(file), "pdf")
            assertEquals(listOf(PdfSearchHit(0, 2), PdfSearchHit(2, 1)), result.hits)
            assertEquals(3, result.occurrences)
            assertTrue(result.hasText)
            assertFalse(result.limited)
        } finally { file.delete() }
    }

    @Test fun emptyPdfDoesNotPretendToHaveSearchableText() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val file = createPdf(app, listOf(null))
        try {
            val result = searchPdfText(app, Uri.fromFile(file), "pdf")
            assertTrue(result.hits.isEmpty())
            assertFalse(result.hasText)
        } finally { file.delete() }
    }

    @Test fun resultAfterPageLimitIsNotReportedAsACompleteSearch() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val texts = List<String?>(SEARCH_PAGE_LIMIT) { null } + "needle"
        val file = createPdf(app, texts)
        try {
            val result = searchPdfText(app, Uri.fromFile(file), "needle")
            assertEquals(SEARCH_PAGE_LIMIT, result.scannedPages)
            assertEquals(SEARCH_PAGE_LIMIT + 1, result.totalPages)
            assertTrue(result.limited)
            assertTrue(result.hits.isEmpty())
        } finally { file.delete() }
    }
}
