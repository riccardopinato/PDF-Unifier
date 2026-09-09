package com.example

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.IOException
import java.util.UUID

/** Call from Dispatchers.IO, after closing the generated PDF. */
internal fun publishPdf(context: Context, source: File, fileName: String): Uri {
    require(fileName.isNotBlank() && fileName == File(fileName).name) {
        "Invalid PDF file name"
    }
    val expectedBytes = source.length()
    if (!source.isFile || expectedBytes <= 0L) {
        throw IOException("The generated PDF is empty or missing")
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_DOCUMENTS}/PDF_Unifier")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), values)
            ?: throw IOException("Unable to create the destination PDF")
        try {
            val copied = (resolver.openOutputStream(uri, "w")
                ?: throw IOException("Unable to open the destination PDF")).use { output ->
                source.inputStream().use { input -> input.copyTo(output) }
            }
            if (copied != expectedBytes) throw IOException("Incomplete PDF write")
            val published = resolver.update(uri, ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }, null, null)
            if (published != 1) throw IOException("Unable to publish the PDF")
            return uri
        } catch (failure: Exception) {
            try {
                resolver.delete(uri, null, null)
            } catch (cleanupFailure: Exception) {
                failure.addSuppressed(cleanupFailure)
            }
            throw failure
        }
    }

    // Android 7–9: retain the existing app-specific storage behavior.
    val root = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        ?: throw IOException("Document storage is unavailable")
    val directory = File(root, "PDF_Unifier/${UUID.randomUUID()}")
    if (!directory.mkdirs()) throw IOException("Unable to create the output folder")
    val destination = File(directory, fileName)
    try {
        source.inputStream().use { input ->
            destination.outputStream().use { output ->
                if (input.copyTo(output) != expectedBytes) {
                    throw IOException("Incomplete PDF write")
                }
            }
        }
        return FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", destination
        )
    } catch (failure: Exception) {
        destination.delete()
        directory.delete()
        throw failure
    }
}
