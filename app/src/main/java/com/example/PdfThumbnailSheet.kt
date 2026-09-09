package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfThumbnailSheet(model: PdfReaderViewModel, onDismiss: () -> Unit) {
    val selected by model.page
    val count by model.count
    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = selected)
    var pageText by rememberSaveable { mutableStateOf((selected + 1).toString()) }
    val target = pageText.toIntOrNull()
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.reader_thumbnails), style = MaterialTheme.typography.titleLarge)
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = pageText, onValueChange = {
                    if (it.length <= 7 && it.all(Char::isDigit)) pageText = it
                }, label = { Text(stringResource(R.string.reader_go_to_page)) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Button(enabled = target != null && target in 1..count, onClick = {
                    target?.takeIf { it in 1..count }?.let { model.page.intValue = it - 1; onDismiss() }
                }) { Text(stringResource(R.string.reader_go)) }
            }
            LazyVerticalGrid(columns = GridCells.Adaptive(120.dp), state = grid,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(count = count, key = { it }) { index ->
                    val image by rememberPdfPage(model, index, 240)
                    OutlinedCard(onClick = { model.page.intValue = index; onDismiss() },
                        border = BorderStroke(if (index == selected) 2.dp else 1.dp,
                            if (index == selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(0.72f).padding(8.dp),
                            contentAlignment = Alignment.Center) {
                            when {
                                image.failed -> Text(stringResource(R.string.reader_thumbnail_failed))
                                image.bitmap == null -> CircularProgressIndicator(Modifier.size(24.dp))
                                else -> Image(checkNotNull(image.bitmap).asImageBitmap(),
                                    stringResource(R.string.reader_page, index + 1),
                                    Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                            }
                        }
                        Text(stringResource(R.string.reader_page, index + 1), Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}
