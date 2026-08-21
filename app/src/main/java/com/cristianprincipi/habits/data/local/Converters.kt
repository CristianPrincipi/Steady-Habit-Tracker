package com.cristianprincipi.habits.data.local

import androidx.room.TypeConverter
import com.cristianprincipi.habits.data.local.entity.SyncState

/**
 * The one conversion this schema needs.
 *
 * Everything else is already a primitive by design, not by accident: dates are stored as epoch days
 * and times as epoch millis (`CLAUDE.md` §6), so there is no `LocalDate` or `Instant` converter here
 * and there should not be. A converter that silently turned a timestamp into a completion date is
 * exactly the bug §6 is written to prevent.
 *
 * [SyncState] is stored by name rather than by ordinal. An ordinal would make reordering the enum a
 * silent data corruption; a name makes it a loud [IllegalArgumentException].
 */
class Converters {
    @TypeConverter
    fun syncStateToName(state: SyncState): String = state.name

    @TypeConverter
    fun syncStateFromName(name: String): SyncState = SyncState.valueOf(name)
}
