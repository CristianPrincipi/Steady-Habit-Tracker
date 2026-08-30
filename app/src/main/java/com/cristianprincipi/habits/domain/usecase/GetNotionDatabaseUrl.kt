package com.cristianprincipi.habits.domain.usecase

import com.cristianprincipi.habits.domain.repository.NotionSetupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Where the user's habit database lives in Notion, for the one screen that links out to it.
 *
 * ⭐ **This exists to make the screen's reach small, not to add a layer.** The value is one field on
 * [NotionSetupRepository], and injecting that repository instead would hand a read-only habit list
 * `saveToken`, `saveMapping` and `disconnect` — the whole connection, wired into a screen whose only
 * write is a colour. [SetHabitColor]'s KDoc makes the same argument about `HabitRepository`.
 *
 * `null` means the app has not learned the id yet — a connection from an older build, or one whose
 * first sync has not landed. Not an error, and not worth a state of its own.
 */
class GetNotionDatabaseUrl
    @Inject
    constructor(
        private val connection: NotionSetupRepository,
    ) {
        operator fun invoke(): Flow<String?> = connection.databaseUrl
    }
