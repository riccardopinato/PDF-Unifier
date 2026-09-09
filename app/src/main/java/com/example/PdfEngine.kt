package com.example

import android.content.Context
import android.net.Uri

internal enum class PdfAction { MERGE, IMAGE_TO_PDF, SPLIT, LOCK, ROTATE }

// A normal class deliberately avoids generating a toString containing the password.
internal class PdfRequest(
    val action: PdfAction,
    uris: List<Uri>,
    val compress: Boolean = false,
    val fromPage: Int = 1,
    val toPage: Int = 1,
    val angle: Int = 90,
    val password: String = ""
) {
    val uris: List<Uri> = uris.toList()
}

internal interface PdfEngine {
    suspend fun generate(request: PdfRequest): Pair<Uri, String>
    suspend fun pageCount(uri: Uri): Int
    suspend fun extractText(uriOrPath: String): String
}

internal class AndroidPdfEngine(context: Context) : PdfEngine {
    private val app = context.applicationContext

    override suspend fun generate(request: PdfRequest): Pair<Uri, String> = with(request) {
        require(uris.isNotEmpty()) { "No input documents" }
        when (action) {
            PdfAction.MERGE -> {
                require(uris.size >= 2) { "Select at least two PDFs" }
                mergeMultiplePdfs(app, uris, compress)
            }
            PdfAction.IMAGE_TO_PDF -> convertImagesToPdf(app, uris, compress)
            PdfAction.SPLIT -> splitPdf(app, uris.single(), fromPage, toPage, compress)
            PdfAction.LOCK -> {
                require(password.isNotEmpty()) { "Empty password" }
                protectPdfWithPassword(app, uris.single(), password)
            }
            PdfAction.ROTATE -> {
                require(angle in listOf(90, 180, 270)) { "Invalid rotation" }
                rotatePdf(app, uris.single(), angle)
            }
        }
    }

    override suspend fun pageCount(uri: Uri): Int = getPdfPageCount(app, uri)
    override suspend fun extractText(uriOrPath: String): String = extractTextFromPdfUri(app, uriOrPath)
}
