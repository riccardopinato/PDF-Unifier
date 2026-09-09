package com.example

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun PdfResultDialog(
    name: String,
    detail: String?,
    warning: String?,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSaveAs: () -> Unit,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(stringResource(R.string.operation_complete_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(name)
                detail?.let { Text(it) }
                warning?.let { Text(it) }
                Button(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.history_open))
                }
                OutlinedButton(onClick = onSaveAs, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pdf_save_as))
                }
                OutlinedButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.history_share))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onClose) { Text(stringResource(R.string.info_close_button)) }
        }
    )
}
