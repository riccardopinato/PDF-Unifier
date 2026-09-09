package com.example

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfBookmarkSheet(model: PdfReaderViewModel, onDismiss: () -> Unit) {
    val state = model.bookmarks.ui
    val current = model.page.intValue
    val saved = current in state.pages
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(0.8f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text(stringResource(R.string.bookmark_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.bookmark_local), style = MaterialTheme.typography.bodySmall)
            }
            item {
                Button(enabled = state.loaded && !state.busy, modifier = Modifier.fillMaxWidth(),
                    onClick = { model.bookmarks.set(current, !saved) }) {
                    Text(stringResource(if (saved) R.string.bookmark_remove_current else R.string.bookmark_add_current,
                        current + 1))
                }
            }
            if (state.busy) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.errorResource?.let { error ->
                item {
                    Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                    TextButton(enabled = !state.busy, onClick = model.bookmarks::refresh) {
                        Text(stringResource(R.string.bookmark_reload))
                    }
                }
            }
            if (state.loaded && state.pages.isEmpty()) item {
                Text(stringResource(R.string.bookmark_empty))
            }
            items(state.pages, key = { it }) { page ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(modifier = Modifier.weight(1f), onClick = {
                        model.page.intValue = page
                        onDismiss()
                    }) { Text(stringResource(R.string.reader_page, page + 1)) }
                    IconButton(enabled = !state.busy, onClick = { model.bookmarks.set(page, false) }) {
                        Icon(Icons.Default.DeleteOutline, stringResource(R.string.bookmark_delete_page, page + 1))
                    }
                }
            }
        }
    }
}
