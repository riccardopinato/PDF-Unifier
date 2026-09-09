package com.example

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PdfOcrDeviceTest {
    @Test fun recognizesHighContrastLatinTextWithBundledModel() = runBlocking {
        val bitmap = Bitmap.createBitmap(1400, 700, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 90f
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }
            canvas.drawText("PDF UNIFIER", 80f, 180f, paint)
            canvas.drawText("12345", 80f, 340f, paint)
            val text = recognizePdfBitmap(bitmap)
            assertTrue(text.contains("PDF", ignoreCase = true))
            assertTrue(text.contains("12345"))
            assertFalse(bitmap.isRecycled)
        } finally { bitmap.recycle() }
    }
}
