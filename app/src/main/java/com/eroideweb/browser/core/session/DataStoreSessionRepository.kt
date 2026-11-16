package com.eroideweb.browser.core.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.io.IOException

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "session")

class DataStoreSessionRepository(context: Context) : SessionRepository {

    private val dataStore = context.applicationContext.sessionDataStore

    override suspend fun load(): SessionSnapshot? {
        val text = try {
            dataStore.data.first()[KEY_SESSION]
        } catch (_: IOException) {
            null
        } ?: return null
        return SessionCodec.decode(text)
    }

    override suspend fun save(snapshot: SessionSnapshot) {
        val encoded = SessionCodec.encode(snapshot)
        dataStore.edit { prefs -> prefs[KEY_SESSION] = encoded }
    }

    private companion object {
        val KEY_SESSION = stringPreferencesKey("session_v1")
    }
}
