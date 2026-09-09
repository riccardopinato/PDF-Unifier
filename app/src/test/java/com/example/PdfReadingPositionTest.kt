package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfReadingPositionTest {
    @Test fun resumesSavedPageAndFallsBackWhenDocumentLengthChanges() {
        assertEquals(7, restoredPdfPage(10, null, PdfReadingPosition(7, 10)))
        assertEquals(0, restoredPdfPage(9, null, PdfReadingPosition(7, 10)))
        assertEquals(0, restoredPdfPage(10, null, null))
    }
    @Test fun androidSessionIncludingFirstPageTakesPrecedence() {
        assertEquals(0, restoredPdfPage(10, 0, PdfReadingPosition(7, 10)))
        assertEquals(3, restoredPdfPage(10, 3, PdfReadingPosition(7, 10)))
        assertEquals(9, restoredPdfPage(10, 20, null))
    }
}
