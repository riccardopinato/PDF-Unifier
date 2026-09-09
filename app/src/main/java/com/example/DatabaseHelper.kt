package com.example

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class HistoryItem(
    val id: Int,
    val fileName: String,
    val fileUriString: String,
    val timestamp: Long,
    val type: String // "MERGE", "SPLIT", "IMAGE_TO_PDF"
)

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "pdf_history.db"
        private const val DATABASE_VERSION = 1
        const val TABLE_HISTORY = "history"
        const val COLUMN_ID = "id"
        const val COLUMN_FILE_NAME = "file_name"
        const val COLUMN_FILE_PATH = "file_path"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_TYPE = "type" // "MERGE", "SPLIT", "IMAGE_TO_PDF"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = ("CREATE TABLE $TABLE_HISTORY ("
                + "$COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "$COLUMN_FILE_NAME TEXT, "
                + "$COLUMN_FILE_PATH TEXT, "
                + "$COLUMN_TIMESTAMP INTEGER, "
                + "$COLUMN_TYPE TEXT)")
        db.execSQL(createTable)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_HISTORY")
        onCreate(db)
    }

    /**
     * Inserisce un nuovo file generato nella cronologia
     */
    fun insertHistory(fileName: String, filePathOrUri: String, type: String): Long {
        val db = this.writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_FILE_NAME, fileName)
            put(COLUMN_FILE_PATH, filePathOrUri)
            put(COLUMN_TIMESTAMP, System.currentTimeMillis())
            put(COLUMN_TYPE, type)
        }
        val id = db.insert(TABLE_HISTORY, null, values)
        db.close()
        return id
    }

    /**
     * Recupera la lista cronologica ordinata dal più recente al più vecchio
     */
    fun getHistory(): List<HistoryItem> {
        val list = mutableListOf<HistoryItem>()
        val db = this.readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_HISTORY ORDER BY $COLUMN_TIMESTAMP DESC", null)
        if (cursor.moveToFirst()) {
            do {
                val item = HistoryItem(
                    id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_ID)),
                    fileName = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_NAME)),
                    fileUriString = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_FILE_PATH)),
                    timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_TIMESTAMP)),
                    type = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_TYPE))
                )
                list.add(item)
            } while (cursor.moveToNext())
        }
        cursor.close()
        db.close()
        return list
    }

    /**
     * Elimina un singolo elemento specifico della cronologia
     */
    fun deleteHistoryItem(id: Int) {
        val db = this.writableDatabase
        db.delete(TABLE_HISTORY, "$COLUMN_ID = ?", arrayOf(id.toString()))
        db.close()
    }

    /**
     * Svuota l'intera cronologia
     */
    fun clearAllHistory() {
        val db = this.writableDatabase
        db.delete(TABLE_HISTORY, null, null)
        db.close()
    }
}
