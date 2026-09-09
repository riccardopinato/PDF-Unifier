package com.example

import android.content.Context
import android.net.Uri
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.Writer

internal const val SEARCH_PAGE_LIMIT = 500
internal data class PdfSearchHit(val page: Int, val occurrences: Int)
internal data class PdfSearchResult(
    val hits: List<PdfSearchHit>, val scannedPages: Int, val totalPages: Int,
    val hasText: Boolean
) {
    val occurrences: Int get() = hits.sumOf { it.occurrences }
    val limited: Boolean get() = scannedPages < totalPages
}

// Literal, non-overlapping matches; ignore case but preserve accents.
internal fun countPdfMatches(text: String, query: String): Int {
    if (query.isBlank()) return 0
    var offset = 0
    var count = 0
    while (offset <= text.length - query.length) {
        val found = text.indexOf(query, offset, ignoreCase = true)
        if (found < 0) break
        count++
        offset = found + query.length
    }
    return count
}

private class SearchPageLimitReached : RuntimeException()
internal class PdfSearchPageTooComplex : RuntimeException()

internal suspend fun searchPdfText(context: Context, uri: Uri, query: String): PdfSearchResult =
    withContext(Dispatchers.IO) {
        val term = query.trim()
        require(term.isNotEmpty() && term.length <= 120)
        val coroutine = currentCoroutineContext()
        // A separate snapshot keeps search independent of the renderer lifecycle.
        val file = stagePdf(context, uri)
        try {
            PDFBoxResourceLoader.init(context.applicationContext)
            val memory = MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)
            PDDocument.load(file, memory).use { document ->
                coroutine.ensureActive()
                if (!document.currentAccessPermission.canExtractContent()) throw IOException("Text extraction unavailable")
                val total = document.numberOfPages
                require(total > 0)
                val scanned = minOf(total, SEARCH_PAGE_LIMIT)
                val hits = mutableListOf<PdfSearchHit>()
                val buffer = StringBuilder()
                var hasText = false
                val writer = object : Writer() {
                    override fun write(chars: CharArray, offset: Int, length: Int) {
                        coroutine.ensureActive()
                        if (buffer.length + length > 500_000) throw PdfSearchPageTooComplex()
                        buffer.append(chars, offset, length)
                    }
                    override fun flush() = Unit
                    override fun close() = Unit
                }
                val stripper = object : PDFTextStripper() {
                    private var glyphs = 0
                    override fun processPage(page: PDPage) {
                        coroutine.ensureActive()
                        if (currentPageNo > SEARCH_PAGE_LIMIT) throw SearchPageLimitReached()
                        super.processPage(page)
                    }
                    override fun startPage(page: PDPage) {
                        buffer.setLength(0)
                        glyphs = 0
                        super.startPage(page)
                    }
                    override fun processTextPosition(text: TextPosition) {
                        coroutine.ensureActive()
                        if (++glyphs > 100_000) throw PdfSearchPageTooComplex()
                        super.processTextPosition(text)
                    }
                    override fun endPage(page: PDPage) {
                        coroutine.ensureActive()
                        val text = buffer.toString()
                        if (text.isNotBlank()) hasText = true
                        val matches = countPdfMatches(text, term)
                        if (matches > 0) hits.add(PdfSearchHit(currentPageNo - 1, matches))
                        buffer.setLength(0)
                        super.endPage(page)
                    }
                }
                stripper.sortByPosition = true
                stripper.endPage = scanned
                try { stripper.writeText(document, writer) }
                catch (_: SearchPageLimitReached) { /* The UI explicitly labels partial results. */ }
                coroutine.ensureActive()
                PdfSearchResult(hits.toList(), scanned, total, hasText)
            }
        } finally {
            file.delete()
        }
    }
