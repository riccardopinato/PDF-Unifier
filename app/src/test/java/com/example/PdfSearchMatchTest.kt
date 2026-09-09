package com.example

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfSearchMatchTest {
    @Test fun matchesAreLiteralAndCaseInsensitive() {
        assertEquals(2, countPdfMatches("PDF e pdf", "PdF"))
        assertEquals(1, countPdfMatches("a.b axb", "a.b"))
        assertEquals(1, countPdfMatches("caffè caffe", "caffè"))
    }
    @Test fun emptyAndOverlappingQueriesHaveDefinedResults() {
        assertEquals(0, countPdfMatches("abc", ""))
        assertEquals(0, countPdfMatches("abc", "abcdef"))
        assertEquals(2, countPdfMatches("aaaaa", "aa"))
    }
}
