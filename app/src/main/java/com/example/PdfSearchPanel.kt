package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

@Composable
internal fun PdfSearchPanel(controller: PdfSearchController, onPage: (Int) -> Unit) {
    val state = controller.ui
    val keyboard = LocalSoftwareKeyboardController.current
    val submit = { keyboard?.hide(); controller.submit() }
    Column(Modifier.fillMaxWidth().heightIn(max = 200.dp).verticalScroll(rememberScrollState())
        .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(value = state.query, onValueChange = controller::edit,
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(stringResource(R.string.search_label)) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submit() }),
            trailingIcon = {
                if (state.running) TextButton(onClick = controller::cancel) {
                    Text(stringResource(R.string.search_cancel))
                } else TextButton(enabled = state.query.isNotBlank(), onClick = { submit() }) {
                    Text(stringResource(R.string.search_action))
                }
            })
        if (state.running) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(stringResource(R.string.search_running), style = MaterialTheme.typography.bodySmall)
        }
        state.errorResource?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        state.result?.let { result ->
            Text(when {
                !result.hasText -> stringResource(R.string.search_no_text)
                result.hits.isEmpty() -> stringResource(R.string.search_none)
                else -> stringResource(R.string.search_summary, result.occurrences, result.hits.size)
            }, style = MaterialTheme.typography.bodySmall)
            if (result.limited) Text(stringResource(R.string.search_partial, result.scannedPages, result.totalPages),
                style = MaterialTheme.typography.bodySmall)
            if (result.hits.isNotEmpty()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = state.selected > 0, onClick = {
                        controller.move(-1)?.let(onPage)
                    }) { Text(stringResource(R.string.search_previous)) }
                    TextButton(enabled = state.selected < result.hits.lastIndex, onClick = {
                        controller.move(1)?.let(onPage)
                    }) { Text(stringResource(if (state.selected < 0) R.string.search_first else R.string.search_next)) }
                }
                result.hits.getOrNull(state.selected)?.let { hit ->
                    Text(stringResource(R.string.search_selected, hit.page + 1, hit.occurrences),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
