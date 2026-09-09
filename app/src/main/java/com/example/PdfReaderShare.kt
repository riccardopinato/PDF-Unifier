package com.example

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast

internal fun pdfReaderShareChooser(uri: Uri, name: String, title: String): Intent {
    require(uri.scheme == "content" && !uri.authority.isNullOrBlank()) {
        "Sharing requires a content URI"
    }
    val label = name.ifBlank { "PDF" }.take(200)
    val content = ClipData.newRawUri(label, uri)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, label)
        clipData = content
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    return Intent.createChooser(send, title).apply {
        clipData = content
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

internal fun sharePdfFromReader(activity: Activity, uri: Uri, name: String) {
    try {
        activity.startActivity(pdfReaderShareChooser(uri, name, activity.getString(R.string.reader_share_pdf)))
    } catch (_: Exception) {
        Toast.makeText(activity, R.string.reader_share_failed, Toast.LENGTH_LONG).show()
    }
}
