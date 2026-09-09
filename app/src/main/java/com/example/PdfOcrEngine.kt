package com.example

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

private val pdfOcrMutex = Mutex()
internal class PdfOcrTextTooLong : IOException()

internal suspend fun recognizePdfBitmap(bitmap: Bitmap): String = pdfOcrMutex.withLock {
    withContext(Dispatchers.Default) {
        currentCoroutineContext().ensureActive()
        require(!bitmap.isRecycled && bitmap.width.toLong() * bitmap.height <= 2_000_000)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            // This bitmap belongs to PdfRenderStore. Never recycle or modify it here.
            // Wait for ML Kit completion even after cancellation before closing the client.
            val text = suspendCoroutine<String> { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0)).addOnCompleteListener { task ->
                    if (task.isSuccessful) continuation.resume(task.result.text)
                    else continuation.resumeWithException(task.exception ?: IOException("OCR did not complete"))
                }
            }
            currentCoroutineContext().ensureActive()
            if (text.length > 50_000) throw PdfOcrTextTooLong()
            text.trim()
        } finally {
            recognizer.close()
        }
    }
}
