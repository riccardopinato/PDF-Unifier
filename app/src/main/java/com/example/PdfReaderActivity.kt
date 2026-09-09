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
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import com.example.ui.theme.MyApplicationTheme
import java.io.File

class PdfReaderActivity : ComponentActivity() {
    private lateinit var reader: PdfReaderViewModel
    private var pendingOcrTextToken by mutableStateOf<String?>(null)
    private var preparingOcrText by mutableStateOf(false)

    // Register unconditionally so Android can deliver a result after Activity recreation.
    private val createOcrText = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { destination ->
        val token = pendingOcrTextToken
        pendingOcrTextToken = null
        if (::reader.isInitialized) reader.ocrExport.finish(token, destination)
    }

    private fun exportOcrText(text: String, page: Int, documentName: String) {
        if (preparingOcrText || pendingOcrTextToken != null || reader.ocrExport.busy) return
        if (text.isBlank() || text.length > 50_000 || page < 0) return
        preparingOcrText = true
        reader.ocrExport.notice?.let { reader.ocrExport.clearNotice(it.id) }
        lifecycleScope.launch {
            var token: String? = null
            var launched = false
            try {
                token = reader.ocrExport.prepare(text)
                check(lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED))
                pendingOcrTextToken = token
                createOcrText.launch(ocrTextFileName(documentName, page))
                launched = true
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) {
                pendingOcrTextToken = null
                reader.ocrExport.reportFailure(R.string.ocr_export_prepare_failed)
            } finally {
                val unused = token
                if (!launched && unused != null) withContext(NonCancellable) { reader.ocrExport.discard(unused) }
                preparingOcrText = false
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingOcrTextToken = savedInstanceState?.getString("pending_ocr_text_token")
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
                    onShare = { sharePdfFromReader(this, uri, name) },
                    ocrExportBusy = preparingOcrText || pendingOcrTextToken != null || reader.ocrExport.busy,
                    onSaveOcrText = { text, page -> exportOcrText(text, page, name) })
            }
        }
    }

    override fun onStop() {
        if (::reader.isInitialized) reader.persistPosition()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        pendingOcrTextToken?.let { outState.putString("pending_ocr_text_token", it) }
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
