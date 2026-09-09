package com.example

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import com.tom_roush.pdfbox.cos.COSBase
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.Collections
import java.util.IdentityHashMap

internal data class CompressionReport(
    val beforeBytes: Long,
    val afterBytes: Long,
    val optimizedImages: Int,
    val keptOriginal: Boolean
) {
    fun summary(context: Context): String = if (keptOriginal) {
        context.getString(R.string.pdf_compress_unchanged)
    } else {
        context.getString(R.string.pdf_compress_report,
            Formatter.formatShortFileSize(context, beforeBytes),
            Formatter.formatShortFileSize(context, afterBytes), optimizedImages)
    }
}

internal data class CompressedPdf(val uri: Uri, val name: String, val report: CompressionReport)

internal fun isUsefulCompression(before: Long, after: Long): Boolean =
    before > 0 && after > 0 && after <= before * 0.98

/** Conservative optimization, never writes to the input. Run on IO. */
internal fun compressPdfOffline(inputFile: File, outputFile: File): CompressionReport {
    require(inputFile.canonicalFile != outputFile.canonicalFile) { "Input and output must differ" }
    val before = inputFile.length()
    require(before in 1..MAX_PDF_BYTES) { "PDF must be between 1 byte and 256 MiB" }
    val candidate = File.createTempFile("pdf_candidate_", ".pdf", inputFile.parentFile)
    var optimized = 0
    var pages = 0
    try {
        val memory = MemoryUsageSetting.setupMixed(24L * 1024 * 1024).setTempDir(inputFile.parentFile)
        PDDocument.load(inputFile, memory).use { document ->
            pages = document.numberOfPages
            // Do not reserialize signed, encrypted or form-bearing input documents.
            if (!document.isEncrypted && document.signatureDictionaries.isEmpty() &&
                document.documentCatalog.acroForm == null) {
                val visited = Collections.newSetFromMap(IdentityHashMap<COSBase, Boolean>())
                val replacements = IdentityHashMap<COSBase, PDImageXObject?>()
                var resourceCount = 0

                fun replacement(image: PDImageXObject): PDImageXObject? {
                    val key = image.cosObject
                    if (replacements.containsKey(key)) return replacements[key]
                    replacements[key] = null
                    val excluded = listOf("SMask", "Mask", "Decode", "Intent", "Alternates",
                        "OC", "StructParent", "Matte", "SMaskInData")
                    if (image.isStencil || image.bitsPerComponent != 8 ||
                        image.width !in 1..4096 || image.height !in 1..4096 ||
                        image.width.toLong() * image.height > 4_000_000L ||
                        excluded.any { key.containsKey(COSName.getPDFName(it)) } ||
                        image.colorSpace !is PDDeviceRGB || key.length < 100_000L) return null
                    val bitmap = image.image ?: return null
                    try {
                        if (bitmap.hasAlpha()) {
                            val row = IntArray(bitmap.width)
                            for (y in 0 until bitmap.height) {
                                bitmap.getPixels(row, 0, bitmap.width, 0, y, bitmap.width, 1)
                                if (row.any { it ushr 24 != 255 }) return null
                            }
                            bitmap.setHasAlpha(false)
                        }
                        val jpeg = JPEGFactory.createFromImage(document, bitmap, 0.85f)
                        if (jpeg.cosObject.length >= key.length) return null
                        // Preserve interpolation and image metadata that are not encoding-specific.
                        for (name in listOf("Interpolate", "Metadata")) {
                            val entry = COSName.getPDFName(name)
                            key.getItem(entry)?.let { jpeg.cosObject.setItem(entry, it) }
                        }
                        replacements[key] = jpeg
                        optimized++
                        return jpeg
                    } finally {
                        bitmap.recycle()
                    }
                }

                fun visit(resources: PDResources?, depth: Int) {
                    if (resources == null || depth > 32 || !visited.add(resources.cosObject)) return
                    if (++resourceCount > 10_000) throw IOException("PDF resource graph too large")
                    for (name in resources.xObjectNames.toList()) {
                        when (val item = resources.getXObject(name)) {
                            is PDImageXObject -> replacement(item)?.let { resources.put(name, it) }
                            is PDFormXObject -> visit(item.resources, depth + 1)
                        }
                    }
                }
                for (page in document.pages) visit(page.resources, 0)
                if (optimized > 0) document.save(candidate)
            }
        }
        val useCandidate = optimized > 0 && isUsefulCompression(before, candidate.length())
        if (useCandidate) {
            PDDocument.load(candidate, MemoryUsageSetting.setupTempFileOnly().setTempDir(inputFile.parentFile)).use {
                if (it.numberOfPages != pages) throw IOException("PDF page count changed during compression")
            }
        }
        (if (useCandidate) candidate else inputFile).copyTo(outputFile, overwrite = true)
        return CompressionReport(before, outputFile.length(), if (useCandidate) optimized else 0, !useCandidate)
    } finally {
        candidate.delete()
    }
}

internal suspend fun compressExistingPdf(context: Context, uri: Uri): CompressedPdf = withContext(Dispatchers.IO) {
    val input = stagePdf(context, uri)
    try {
        val output = File.createTempFile("pdf_compressed_", ".pdf", context.cacheDir)
        try {
            val report = compressPdfOffline(input, output)
            val name = "PDF_Compatto_${System.currentTimeMillis()}.pdf"
            CompressedPdf(publishPdf(context, output, name), name, report)
        } finally { output.delete() }
    } finally { input.delete() }
}
