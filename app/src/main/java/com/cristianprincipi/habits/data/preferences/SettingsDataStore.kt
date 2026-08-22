package com.cristianprincipi.habits.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's preferences, which in v1 is one value.
 *
 * That is the honest size, not an oversight — see [com.cristianprincipi.habits.domain.repository.SettingsRepository]
 * for where every other candidate went. Theme mode: dark-only (ADR-009). Reminders: post-v1. Week
 * start: a constant, not a preference ([com.cristianprincipi.habits.core.datetime.WeekStart]).
 * Onboarding-seen: there is no onboarding.
 *
 * **The Notion property mapping is not here yet, and putting it here early would mean typing it
 * twice.** `docs/notion-schema.md` §6.1 says to key the mapping on each property's id rather than
 * its name, and whether those ids survive a rename is a question M4.1 answers against the live API.
 * The sync cursor is in the same position: its form follows the query endpoint that M4.1 confirms.
 *
 * **Primitives only.** This class knows about `Long`s and preference keys; `SettingsRepositoryImpl`
 * turns them into `Instant`s. It is the same boundary Room entities have — storage types stop here.
 */
@Singleton
class SettingsDataStore
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) {
        /**
         * When the last successful sync finished, or null if none ever has.
         *
         * **A read failure emits empty preferences rather than throwing.** DataStore signals a
         * missing or unreadable file as an [IOException] on the flow, and this value drives one
         * line of text on the Settings screen. Letting that take the screen down — or worse, the
         * collector that M4 hangs its scheduling off — would trade a wrong caption for a crash.
         * Anything that is not an [IOException] is a real bug and is left to propagate.
         */
        val lastSyncedAtEpochMillis: Flow<Long?> =
            dataStore.data
                .catch { cause ->
                    if (cause is IOException) emit(emptyPreferences()) else throw cause
                }.map { preferences -> preferences[LAST_SYNCED_AT] }

        suspend fun setLastSyncedAtEpochMillis(millis: Long) {
            dataStore.edit { preferences -> preferences[LAST_SYNCED_AT] = millis }
        }

        private companion object {
            /**
             * The stored name carries its unit.
             *
             * A key called `last_synced_at` would not say whether the number under it is seconds,
             * millis or an epoch day, and this file outlives the code that wrote it.
             */
            val LAST_SYNCED_AT = longPreferencesKey("last_synced_at_epoch_millis")
        }
    }
