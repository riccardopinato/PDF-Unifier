package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfOcrSheet(controller: PdfOcrController) {
    val state = controller.ui
    val context = LocalContext.current
    var copied by remember(state.text) { mutableStateOf(false) }
    var copyFailed by remember(state.text) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = controller::dismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.ocr_page_title, state.page + 1),
                style = MaterialTheme.typography.titleLarge)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.ocr_description), style = MaterialTheme.typography.bodySmall)
                when {
                    state.running -> {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Text(stringResource(R.string.ocr_running))
                    }
                    state.failed -> {
                        Text(stringResource(if (state.tooLong) R.string.ocr_too_long else R.string.ocr_failed),
                            color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { controller.start(state.page) }) {
                            Text(stringResource(R.string.ocr_retry))
                        }
                    }
                    state.text.isNullOrBlank() -> Text(stringResource(R.string.ocr_empty))
                    else -> SelectionContainer { Text(state.text.orEmpty()) }
                }
            }
            if (copied) Text(stringResource(R.string.ocr_copied))
            if (copyFailed) Text(stringResource(R.string.ocr_copy_failed), color = MaterialTheme.colorScheme.error)
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(modifier = Modifier.weight(1f), onClick = controller::dismiss) {
                    Text(stringResource(if (state.running) R.string.ocr_cancel else R.string.ocr_close))
                }
                Button(modifier = Modifier.weight(1f),
                    enabled = !state.running && !state.failed && !state.text.isNullOrBlank(), onClick = {
                        try {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("PDF OCR", state.text.orEmpty())
                            // Hide document text in clipboard previews on Android versions that support this hint.
                            clip.description.extras = PersistableBundle().apply {
                                putBoolean("android.content.extra.IS_SENSITIVE", true)
                            }
                            clipboard.setPrimaryClip(clip)
                            copied = true
                            copyFailed = false
                        } catch (_: Exception) { copied = false; copyFailed = true }
                    }) { Text(stringResource(R.string.ocr_copy)) }
            }
        }
    }
}
