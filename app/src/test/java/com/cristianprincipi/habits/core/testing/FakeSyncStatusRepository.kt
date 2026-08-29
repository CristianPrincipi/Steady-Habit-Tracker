package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.domain.model.DiscardedEditNotice
import com.cristianprincipi.habits.domain.model.SyncStatus
import com.cristianprincipi.habits.domain.repository.SyncStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [SyncStatusRepository] that answers from two flows and counts what it was asked to do.
 *
 * Shared rather than private to one test because M6.8 gave the Week screen a second reason to hold
 * one: with no habits in Room, `WeekViewModel` reads the status to say whether the first import is
 * on its way or has failed. Both tests want to push a status and watch a screen change, which is
 * the whole of what this does.
 */
class FakeSyncStatusRepository(
    private val status: MutableStateFlow<SyncStatus> = MutableStateFlow(SyncStatus.Idle(lastSyncedAt = null)),
    private val notices: MutableStateFlow<List<DiscardedEditNotice>> = MutableStateFlow(emptyList()),
) : SyncStatusRepository {
    var syncRequests = 0
        private set

    var dismissals = 0
        private set

    override fun observeStatus(): Flow<SyncStatus> = status

    override fun requestSync() {
        syncRequests++
    }

    override fun observeDiscardedEdits(): Flow<List<DiscardedEditNotice>> = notices

    override suspend fun dismissDiscardedEdits() {
        dismissals++
        notices.value = emptyList()
    }
}
