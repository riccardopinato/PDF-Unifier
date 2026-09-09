package com.example

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.example.ui.theme.MyApplicationTheme
import java.io.File

class PdfReaderActivity : ComponentActivity() {
    private lateinit var reader: PdfReaderViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri = intent.data ?: run { finish(); return }
        reader = ViewModelProvider(this,
            PdfReaderViewModel.factory(applicationContext, savedInstanceState?.takeIf { it.containsKey("reader_page") }?.getInt("reader_page"))
        )[PdfReaderViewModel::class.java]
        reader.load(uri)
        val name = intent.getStringExtra(Intent.EXTRA_TITLE) ?: "PDF"
        setContent {
            MyApplicationTheme {
                PdfReaderScreen(reader, name, onBack = { finish() },
                    onExternal = { openPdfExternally(this, uri) },
                    onShare = { sharePdfFromReader(this, uri, name) })
            }
        }
    }

    override fun onStop() {
        if (::reader.isInitialized) reader.persistPosition()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::reader.isInitialized && reader.count.intValue > 0) outState.putInt("reader_page", reader.page.intValue)
        super.onSaveInstanceState(outState)
    }
}

internal fun openPdfReader(context: Context, uriOrPath: String, name: String) {
    try {
        val parsed = Uri.parse(uriOrPath)
        val uri = when (parsed.scheme) {
            "content" -> parsed
            "file" -> FileProvider.getUriForFile(context, "${context.packageName}.fileprovider",
                File(requireNotNull(parsed.path)))
            null -> FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(uriOrPath))
            else -> throw IllegalArgumentException("Unsupported document URI")
        }
        context.startActivity(Intent(context, PdfReaderActivity::class.java).apply {
            data = uri
            putExtra(Intent.EXTRA_TITLE, name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    } catch (_: Exception) {
        Toast.makeText(context, R.string.reader_cannot_open, Toast.LENGTH_LONG).show()
    }
}

internal fun openPdfExternally(context: Context, uri: Uri) {
    try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            clipData = ClipData.newRawUri("PDF", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.reader_external)))
    } catch (_: Exception) {
        Toast.makeText(context, R.string.no_pdf_viewer_app, Toast.LENGTH_LONG).show()
    }
}
