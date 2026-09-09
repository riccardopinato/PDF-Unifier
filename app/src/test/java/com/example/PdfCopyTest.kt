package com.example

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

class PdfCopyTest {
    @Test fun keepsEverySourceByte() = runTest {
        val bytes = ByteArray(200000) { (it % 251).toByte() }
        val output = ByteArrayOutputStream()
        assertEquals(bytes.size.toLong(), copyPdfBytes(ByteArrayInputStream(bytes), output))
        assertArrayEquals(bytes, output.toByteArray())
    }
    @Test fun rejectsAnEmptySource() = runTest {
        try {
            copyPdfBytes(ByteArrayInputStream(byteArrayOf()), ByteArrayOutputStream())
            fail("An empty source must fail")
        } catch (_: IOException) { }
    }
    @Test fun normalizesPdfNames() {
        assertEquals("Document.pdf", pdfCopyName(" "))
        assertEquals("scan.pdf", pdfCopyName("scan.PDF"))
        assertEquals("a_b.pdf", pdfCopyName("a/b"))
        assertEquals("a_b.pdf", pdfCopyName("a\\b"))
    }
}
