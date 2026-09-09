package com.example

import org.junit.Assert.*
import org.junit.Test

class PdfCompressionPolicyTest {
    @Test fun rejectsLargerOrInsignificantlySmallerCandidate() {
        assertFalse(isUsefulCompression(10000, 10001))
        assertFalse(isUsefulCompression(10000, 9900))
        assertTrue(isUsefulCompression(10000, 9800))
        assertFalse(isUsefulCompression(10000, 0))
    }
}
