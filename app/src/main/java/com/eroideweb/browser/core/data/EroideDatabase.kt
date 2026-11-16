package com.eroideweb.browser.core.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Database locale unico. Nessun dato esce dall'app.
 * La versione resta 1 finché non esiste una release pubblica: le tabelle
 * nuove si aggiungono qui prima di spedire la prima build con Room.
 */
@Database(
    entities = [HistoryEntry::class, BookmarkEntry::class, DownloadEntry::class],
    version = 1,
    exportSchema = false,
)
abstract class EroideDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadDao(): DownloadDao

    companion object {
        @Volatile
        private var instance: EroideDatabase? = null

        fun get(context: Context): EroideDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    EroideDatabase::class.java,
                    "eroideweb.db",
                ).build().also { instance = it }
            }
    }
}
