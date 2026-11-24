package com.eroideweb.browser.core.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Database locale unico. Nessun dato esce dall'app.
 *
 * Gli schemi sono esportati in `app/schemas/`: ogni tabella nuova alza la
 * versione con una AutoMigration, così chi aggiorna l'app conserva
 * cronologia, segnalibri e download.
 */
@Database(
    entities = [
        HistoryEntry::class,
        BookmarkEntry::class,
        DownloadEntry::class,
        FavoriteEntry::class,
        CredentialEntry::class,
    ],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // v2: preferiti della pagina iniziale.
        AutoMigration(from = 1, to = 2),
        // v3: password salvate (cifrate).
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class EroideDatabase : RoomDatabase() {

    abstract fun historyDao(): HistoryDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun downloadDao(): DownloadDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun credentialDao(): CredentialDao

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
