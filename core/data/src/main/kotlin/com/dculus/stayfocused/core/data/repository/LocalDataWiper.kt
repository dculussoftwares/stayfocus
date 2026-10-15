package com.dculus.stayfocused.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.dculus.stayfocused.core.data.db.StayFocusedDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Erases everything this app stores on the device, when the account is deleted. */
fun interface LocalDataWiper {
    /** Clears every Room table and the settings DataStore (onboarding shows again on next start). */
    suspend fun wipe()
}

internal class RoomDataStoreWiper
    @Inject
    constructor(
        private val database: StayFocusedDatabase,
        private val store: DataStore<Preferences>,
    ) : LocalDataWiper {
        override suspend fun wipe() {
            // Both are attempted even if the first fails; the first failure is rethrown afterwards.
            val tables = runCatching { withContext(Dispatchers.IO) { database.clearAllTables() } }
            val settings = runCatching { store.edit { it.clear() } }
            (tables.exceptionOrNull() ?: settings.exceptionOrNull())?.let { throw it }
        }
    }
