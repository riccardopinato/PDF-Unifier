package com.example

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

internal data class PdfPageImage(val bitmap: Bitmap? = null, val failed: Boolean = false)

@Composable
internal fun rememberPdfPage(model: PdfReaderViewModel, index: Int, edge: Int): State<PdfPageImage> =
    produceState(PdfPageImage(), model, index, edge) {
        value = PdfPageImage()
        try {
            value = PdfPageImage(model.render(index, edge))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            value = PdfPageImage(failed = true)
        }
    }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfReaderScreen(model: PdfReaderViewModel, name: String, onBack: () -> Unit, onExternal: () -> Unit) {
    val count by model.count
    val page by model.page
    val error by model.error
    val positionWarning by model.positionWarning
    val snackbar = remember { SnackbarHostState() }
    val positionErrorText = stringResource(R.string.reading_position_error)
    LaunchedEffect(positionWarning) {
        if (positionWarning) {
            snackbar.showSnackbar(positionErrorText)
            model.positionWarning.value = false
        }
    }
    var showBookmarks by rememberSaveable { mutableStateOf(false) }
    val bookmarkState = model.bookmarks.ui
    if (showBookmarks && count > 0) {
        PdfBookmarkSheet(model, onDismiss = { showBookmarks = false })
    }
    var showSearch by rememberSaveable { mutableStateOf(false) }
    var showThumbnails by rememberSaveable { mutableStateOf(false) }
    if (showThumbnails && count > 0) {
        PdfThumbnailSheet(model, onDismiss = { showThumbnails = false })
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Column {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, stringResource(R.string.reader_back))
                    }
                },
                actions = {
                    IconButton(enabled = count > 0, onClick = {
                        model.bookmarks.refresh()
                        showBookmarks = true
                    }) {
                        Icon(if (page in bookmarkState.pages) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            stringResource(R.string.bookmark_title))
                    }
                    IconButton(enabled = count > 0, onClick = {
                        showSearch = !showSearch
                        if (!showSearch) model.search.cancel()
                    }) {
                        Icon(if (showSearch) Icons.Default.Close else Icons.Default.Search,
                            stringResource(if (showSearch) R.string.search_close else R.string.search_label))
                    }
                    IconButton(enabled = count > 0, onClick = { showThumbnails = true }) {
                        Icon(Icons.Default.GridView, stringResource(R.string.reader_thumbnails))
                    }
                    IconButton(onClick = onExternal) {
                        Icon(Icons.Default.OpenInNew, stringResource(R.string.reader_external))
                    }
                }
            )
            if (showSearch && count > 0) PdfSearchPanel(model.search) { target ->
                model.page.intValue = target.coerceIn(0, count - 1)
            }
            }
        },
        bottomBar = {
            if (count > 0) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(enabled = page > 0, onClick = { model.page.intValue-- }) {
                        Text(stringResource(R.string.reader_previous))
                    }
                    Text(stringResource(R.string.reader_page_count, page + 1, count))
                    TextButton(enabled = page < count - 1, onClick = { model.page.intValue++ }) {
                        Text(stringResource(R.string.reader_next))
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            when {
                error != null -> Text(error.orEmpty(), modifier = Modifier.padding(24.dp))
                count == 0 -> CircularProgressIndicator()
                else -> key(page) {
                    val image by rememberPdfPage(model, page, 1600)
                    when {
                        image.failed -> Text(stringResource(R.string.reader_page_failed), Modifier.padding(24.dp))
                        image.bitmap == null -> CircularProgressIndicator()
                        else -> PdfZoomablePage(checkNotNull(image.bitmap), page + 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfZoomablePage(bitmap: Bitmap, page: Int) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TextButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f); offset = Offset.Zero }, enabled = zoom > 1f) {
                Text(stringResource(R.string.reader_zoom_out))
            }
            TextButton(onClick = { zoom = 1f; offset = Offset.Zero }) {
                Text(stringResource(R.string.reader_fit))
            }
            TextButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(4f); offset = Offset.Zero }, enabled = zoom < 4f) {
                Text(stringResource(R.string.reader_zoom_in))
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).clipToBounds().onSizeChanged { size = it }
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, factor, _ ->
                    zoom = (zoom * factor).coerceIn(1f, 4f)
                    val boundX = size.width * (zoom - 1f) / 2f
                    val boundY = size.height * (zoom - 1f) / 2f
                    offset = Offset((offset.x + pan.x).coerceIn(-boundX, boundX),
                        (offset.y + pan.y).coerceIn(-boundY, boundY))
                }
            }) {
            Image(bitmap.asImageBitmap(), stringResource(R.string.reader_page, page),
                Modifier.fillMaxSize().graphicsLayer {
                    scaleX = zoom; scaleY = zoom
                    translationX = offset.x; translationY = offset.y
                }, contentScale = ContentScale.Fit)
        }
    }
}
