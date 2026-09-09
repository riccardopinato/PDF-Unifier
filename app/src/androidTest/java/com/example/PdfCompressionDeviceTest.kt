package com.example

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Random

@RunWith(AndroidJUnit4::class)
class PdfCompressionDeviceTest {
    @Test fun transparentImageKeepsOriginalBytes() { checkImage(transparent = true) }
    @Test fun eligibleOpaqueImageProducesSmallerReadablePdf() { checkImage(transparent = false) }

    private fun checkImage(transparent: Boolean) {
        val app = ApplicationProvider.getApplicationContext<Context>()
        PDFBoxResourceLoader.init(app)
        val input = File.createTempFile("compression_test_", ".pdf", app.cacheDir)
        val output = File.createTempFile("compression_out_", ".pdf", app.cacheDir)
        try {
            val bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
            try {
                val random = Random(42)
                val alpha = if (transparent) 0x80000000.toInt() else 0xff000000.toInt()
                val pixels = IntArray(512 * 512) { alpha or random.nextInt(0x1000000) }
                bitmap.setPixels(pixels, 0, 512, 0, 0, 512, 512)
                bitmap.setHasAlpha(transparent)
                PDDocument().use { doc ->
                    val page = PDPage()
                    doc.addPage(page)
                    val image = LosslessFactory.createFromImage(doc, bitmap)
                    PDPageContentStream(doc, page).use { it.drawImage(image, 0f, 0f, 512f, 512f) }
                    doc.save(input)
                }
            } finally { bitmap.recycle() }
            val report = compressPdfOffline(input, output)
            assertTrue(output.length() <= input.length())
            PDDocument.load(output).use { assertEquals(1, it.numberOfPages) }
            if (transparent) {
                assertTrue(report.keptOriginal)
                assertArrayEquals(input.readBytes(), output.readBytes())
            } else {
                assertFalse(report.keptOriginal)
                assertTrue(report.optimizedImages > 0)
            }
        } finally { input.delete(); output.delete() }
    }
}
