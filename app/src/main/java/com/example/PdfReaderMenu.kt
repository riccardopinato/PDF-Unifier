package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource

@Composable
internal fun PdfReaderMenu(
    ready: Boolean,
    bookmarked: Boolean,
    onBookmarks: () -> Unit,
    onThumbnails: () -> Unit,
    onShare: () -> Unit,
    onExternal: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, stringResource(R.string.reader_more_actions))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(enabled = ready,
                text = { Text(stringResource(R.string.reader_thumbnails)) },
                leadingIcon = { Icon(Icons.Default.GridView, contentDescription = null) },
                onClick = { expanded = false; onThumbnails() })
            DropdownMenuItem(enabled = ready,
                text = { Text(stringResource(R.string.bookmark_title)) },
                leadingIcon = { Icon(if (bookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    contentDescription = null) },
                onClick = { expanded = false; onBookmarks() })
            HorizontalDivider()
            DropdownMenuItem(enabled = ready,
                text = { Text(stringResource(R.string.reader_share_pdf)) },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                onClick = { expanded = false; onShare() })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reader_external)) },
                leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) },
                onClick = { expanded = false; onExternal() })
        }
    }
}
