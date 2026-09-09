package com.example

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PdfOcrTextExportTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun suggestedNameUsesTheRecognizedPageAndIsSafe() {
        assertEquals("Report_p3_OCR.txt", ocrTextFileName("Report.PDF", 2))
        assertEquals("Document_p1_OCR.txt", ocrTextFileName(".pdf", 0))
        val name = ocrTextFileName("folder/report:2026.pdf", 1)
        assertFalse(name.contains('/'))
        assertFalse(name.contains(':'))
        assertTrue(name.endsWith("_p2_OCR.txt"))
    }

    @Test fun utf8PreservesAccentsUnicodeAndLineBreaks() {
        val original = "Caffè, città e qualità\nSeconda riga: € 123 — fine"
        assertEquals(original, String(ocrTextUtf8(original), Charsets.UTF_8))
        assertEquals(50_000, ocrTextUtf8("a".repeat(50_000)).size)
        for (invalid in listOf(" \n ", "a".repeat(50_001))) {
            try { ocrTextUtf8(invalid); fail("Expected invalid text") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun pendingSnapshotSurvivesStoreRecreationAndIsIsolated() = runBlocking {
        val directory = temporary.newFolder()
        val first = PdfOcrTextExportStore(directory)
        val tokenA = first.prepare("Testo pagina 2: città")
        val tokenB = first.prepare("Testo pagina 9")
        val recreated = PdfOcrTextExportStore(directory)
        assertEquals("Testo pagina 2: città", String(recreated.read(tokenA), Charsets.UTF_8))
        assertEquals("Testo pagina 9", String(recreated.read(tokenB), Charsets.UTF_8))
        recreated.discard(tokenA)
        assertEquals("Testo pagina 9", String(recreated.read(tokenB), Charsets.UTF_8))
        assertFalse(java.io.File(directory, tokenA).exists())
    }

    @Test fun tokenCannotReadOutsideTheExportFolder() = runBlocking {
        val store = PdfOcrTextExportStore(temporary.newFolder())
        for (token in listOf("../private.txt", "/tmp/private.txt", "not-a-uuid.txt")) {
            try { store.read(token); fail("Expected invalid token") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
