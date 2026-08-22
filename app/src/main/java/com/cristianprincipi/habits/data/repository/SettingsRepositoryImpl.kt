package com.cristianprincipi.habits.data.repository

import com.cristianprincipi.habits.data.preferences.SettingsDataStore
import com.cristianprincipi.habits.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SettingsRepository] over [SettingsDataStore].
 *
 * Thin by construction: the storage layer holds epoch millis, the domain speaks [Instant], and this
 * is the one place the two meet. The same boundary `HabitRepositoryImpl` keeps against Room entities
 * — a `Preferences` key never appears above this class.
 *
 * A wall-clock instant is right here, unlike a completion date. `CLAUDE.md` §6 stores completions as
 * a local day because a habit checked at 23:00 belongs to that day whatever the time zone does; a
 * sync, by contrast, finished at an actual moment, and "3 minutes ago" is what the Settings screen
 * shows. No [java.time.Clock] is involved — the caller passes the instant it observed.
 */
@Singleton
class SettingsRepositoryImpl
    @Inject
    constructor(
        private val settings: SettingsDataStore,
    ) : SettingsRepository {
        override fun observeLastSyncedAt(): Flow<Instant?> =
            settings.lastSyncedAtEpochMillis.map { millis -> millis?.let(Instant::ofEpochMilli) }

        override suspend fun setLastSyncedAt(at: Instant) {
            settings.setLastSyncedAtEpochMillis(at.toEpochMilli())
        }
    }
