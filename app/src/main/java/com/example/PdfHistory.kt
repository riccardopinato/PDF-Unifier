package com.example

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

internal interface PdfHistory {
    suspend fun load(): List<HistoryItem>
    suspend fun add(name: String, uri: String, type: String)
    suspend fun delete(id: Int)
    suspend fun clear()
}

internal class LocalPdfHistory(context: Context) : PdfHistory {
    private val app = context.applicationContext
    private val mutex = Mutex()

    private suspend fun <T> access(block: (DatabaseHelper) -> T): T =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                DatabaseHelper(app).use { helper -> block(helper) }
            }
        }

    override suspend fun load(): List<HistoryItem> = access { it.getHistory() }
    override suspend fun add(name: String, uri: String, type: String) {
        access {
            if (it.insertHistory(name, uri, type) == -1L) {
                throw IOException("History insertion failed")
            }
        }
    }
    override suspend fun delete(id: Int) { access { it.deleteHistoryItem(id) } }
    override suspend fun clear() { access { it.clearAllHistory() } }
}
