package com.cristianprincipi.habits.core.testing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow

/**
 * A [DataStore] that keeps preferences in memory, with none of the file semantics.
 *
 * ADR-032 explains why the JVM tests avoid a real file: DataStore renames a temporary file over the
 * target, and `File.renameTo` on Windows will not overwrite, so a real-file store on this machine
 * accepts exactly one write. The real file is proved on device instead.
 */
class InMemoryDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(state.value).also { state.value = it }
}

/** A [DataStore] whose reads always fail, for the paths a real file cannot easily produce. */
class FailingDataStore(
    private val cause: Throwable,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw cause }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        transform(emptyPreferences())
}
