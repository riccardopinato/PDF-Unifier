package com.example

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PdfReaderShareTest {
    @Suppress("DEPRECATION")
    @Test fun chooserCarriesPdfAndTemporaryReadPermissionWithoutWriteAccess() {
        val uri = Uri.parse("content://test.provider/documents/sample")
        val chooser = pdfReaderShareChooser(uri, "sample.pdf", "Share PDF")
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val send = checkNotNull(chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT))
        assertEquals(Intent.ACTION_SEND, send.action)
        assertEquals("application/pdf", send.type)
        assertEquals(uri, send.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals("sample.pdf", send.getStringExtra(Intent.EXTRA_TITLE))
        for (intent in listOf(send, chooser)) {
            assertEquals(uri, intent.clipData?.getItemAt(0)?.uri)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
            val disallowed = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
            assertEquals(0, intent.flags and disallowed)
            assertNull(intent.component)
            assertNull(intent.`package`)
        }
    }

    @Test fun rejectsFilePathsAndWebLinks() {
        for (source in listOf("file:///data/private.pdf", "https://example.com/document.pdf", "content:///missing-authority")) {
            try {
                pdfReaderShareChooser(Uri.parse(source), "PDF", "Share")
                fail("Expected rejection for $source")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
