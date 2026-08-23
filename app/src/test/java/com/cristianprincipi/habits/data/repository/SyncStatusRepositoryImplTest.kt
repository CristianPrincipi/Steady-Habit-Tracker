package com.cristianprincipi.habits.data.repository

import app.cash.turbine.test
import com.cristianprincipi.habits.core.testing.FakeSyncScheduler
import com.cristianprincipi.habits.core.testing.InMemoryDataStore
import com.cristianprincipi.habits.data.local.dao.FakeHabitDao
import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.data.preferences.SettingsDataStore
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.sync.DiscardedEdit
import com.cristianprincipi.habits.data.sync.DiscardedEditStore
import com.cristianprincipi.habits.data.sync.LastSyncFailureStore
import com.cristianprincipi.habits.data.sync.SyncOutcome
import com.cristianprincipi.habits.data.sync.SyncSide
import com.cristianprincipi.habits.domain.model.DiscardedEditNotice
import com.cristianprincipi.habits.domain.model.EditSide
import com.cristianprincipi.habits.domain.model.SyncFailure
import com.cristianprincipi.habits.domain.model.SyncStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate

/**
 * The one shape the UI sees, assembled from three sources that each know a third of the answer.
 *
 * Everything here is about the **combination**, because that is all this class is: which source
 * wins when two of them have something to say, and what a screen is told while nothing is happening.
 */
class SyncStatusRepositoryImplTest {
    private val syncedAt = Instant.parse("2026-08-23T11:45:00Z")

    private val dataStore = InMemoryDataStore()
    private val settings = SettingsRepositoryImpl(SettingsDataStore(dataStore))
    private val lastFailure = LastSyncFailureStore(dataStore)
    private val scheduler = FakeSyncScheduler()

    private val discardedEdits = DiscardedEditStore(dataStore)
    private val habits = FakeHabitDao()

    private val repository =
        SyncStatusRepositoryImpl(settings, lastFailure, scheduler, discardedEdits, habits)

    @Test
    fun aFreshInstallIsIdleAndHasNeverSynced() =
        runTest {
            // Not "failed" and not "not connected": an app nobody has connected has not gone wrong.
            repository.observeStatus().test {
                assertEquals(SyncStatus.Idle(lastSyncedAt = null), awaitItem())
            }
        }

    @Test
    fun aRunThatWorkedLeavesTheTimeItFinished() =
        runTest {
            settings.setLastSyncedAt(syncedAt)
            lastFailure.record(SyncOutcome.Success(daysPulled = 1, daysPushed = 0))

            repository.observeStatus().test {
                assertEquals(SyncStatus.Idle(syncedAt), awaitItem())
            }
        }

    @Test
    fun aRunInProgressIsSyncing() =
        runTest {
            scheduler.running.value = true

            repository.observeStatus().test {
                assertEquals(SyncStatus.Syncing(lastSyncedAt = null), awaitItem())
            }
        }

    @Test
    fun theLastFailureIsWhatIdleTurnsInto() =
        runTest {
            lastFailure.record(SyncOutcome.Failed(NotionError.MissingOrNotShared))

            repository.observeStatus().test {
                assertEquals(SyncStatus.Failed(SyncFailure.DatabaseNotShared, lastSyncedAt = null), awaitItem())
            }
        }

    @Test
    fun aFailedRunStillReportsWhenTheDataWasLastCorrect() =
        runTest {
            // The line the user actually reads. A sync failing does not make yesterday's data stop
            // having arrived yesterday — which is why lastSyncedAt is on every case, not beside them.
            settings.setLastSyncedAt(syncedAt)
            lastFailure.record(SyncOutcome.Failed(NotionError.Unauthorized))

            repository.observeStatus().test {
                assertEquals(SyncStatus.Failed(SyncFailure.TokenRejected, syncedAt), awaitItem())
            }
        }

    @Test
    fun aRetryInProgressHidesTheErrorItIsRetrying() =
        runTest {
            // The one ordering decision this class makes. The stored failure is still on disk during
            // a retry — it is only replaced when the new run finishes — and showing it over a live
            // attempt would say "broken" at the moment it is being fixed.
            lastFailure.record(SyncOutcome.Failed(NotionError.Server(status = 503)))
            scheduler.running.value = true

            repository.observeStatus().test {
                assertEquals(SyncStatus.Syncing(lastSyncedAt = null), awaitItem())
            }
        }

    @Test
    fun aRunThatStartsAndSucceedsGoesSyncingThenIdle() =
        runTest {
            lastFailure.record(SyncOutcome.Failed(NotionError.Network(IOException())))

            repository.observeStatus().test {
                assertEquals(SyncStatus.Failed(SyncFailure.Offline, lastSyncedAt = null), awaitItem())

                scheduler.running.value = true
                assertEquals(SyncStatus.Syncing(lastSyncedAt = null), awaitItem())

                // The engine's own bookkeeping — it clears the failure and stamps the time before
                // the worker returns, so both land while WorkManager still calls the run RUNNING.
                // The caption updates a moment before the spinner stops, which is the truth.
                lastFailure.record(SyncOutcome.Success(daysPulled = 2, daysPushed = 1))
                settings.setLastSyncedAt(syncedAt)
                assertEquals(SyncStatus.Syncing(syncedAt), awaitItem())

                scheduler.running.value = false
                assertEquals(SyncStatus.Idle(syncedAt), awaitItem())

                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aSourceThatMovesWithoutChangingTheAnswerEmitsNothing() =
        runTest {
            // WorkManager re-emits a work info on every transition, most of which are between two
            // kinds of not-running. Without distinctUntilChanged a status bar recomposes on each.
            repository.observeStatus().test {
                assertEquals(SyncStatus.Idle(lastSyncedAt = null), awaitItem())

                scheduler.running.value = false
                lastFailure.record(SyncOutcome.NotConfigured)

                expectNoEvents()
            }
        }

    // --- the notices that make CLAUDE.md §3.5 true ------------------------------------------

    @Test
    fun aRunThatDestroyedNothingHasNothingToConfess() =
        runTest {
            repository.observeDiscardedEdits().test {
                assertEquals(emptyList<DiscardedEditNotice>(), awaitItem())
            }
        }

    @Test
    fun aNoticeArrivesWithTheNamesThoseHabitsHaveNow() =
        runTest {
            // ⭐ The join is done on READ, not on write (ADR-038): the store keeps ids because names
            // change upstream, and a notice written last week should read with today's name.
            habits.upsert(listOf(habit("h-read", "📖 Reading"), habit("h-journal", "✒️ Journal")))
            discardedEdits.record(
                listOf(
                    DiscardedEdit(
                        epochDay = LocalDate.of(2026, 8, 21).toEpochDay(),
                        habitIds = setOf("h-read", "h-journal"),
                        discardedSide = SyncSide.LOCAL,
                        resolvedAtEpochMillis = 1_755_000_000_000L,
                    ),
                ),
            )

            repository.observeDiscardedEdits().test {
                val notice = awaitItem().single()
                assertEquals(LocalDate.of(2026, 8, 21), notice.date)
                assertEquals(listOf("✒️ Journal", "📖 Reading"), notice.habitNames)
                assertEquals(EditSide.Local, notice.discardedSide)
            }
        }

    @Test
    fun aHabitWhoseColumnWasDeletedLeavesTheCountBehind() =
        runTest {
            // It has no name left to find, so it drops out of the list — but "1 habit affected" is
            // still true and still worth saying, which is why the count travels separately.
            discardedEdits.record(
                listOf(
                    DiscardedEdit(
                        epochDay = 20_000,
                        habitIds = setOf("gone"),
                        discardedSide = SyncSide.REMOTE,
                        resolvedAtEpochMillis = 1_755_000_000_000L,
                    ),
                ),
            )

            repository.observeDiscardedEdits().test {
                val notice = awaitItem().single()
                assertEquals(emptyList<String>(), notice.habitNames)
                assertEquals(1, notice.habitCount)
                assertEquals(EditSide.Remote, notice.discardedSide)
            }
        }

    @Test
    fun dismissingClearsThemAll() =
        runTest {
            // An apology, not a task list — so there is no per-notice dismiss.
            discardedEdits.record(
                listOf(
                    DiscardedEdit(20_000, setOf("a"), SyncSide.LOCAL, 1L),
                    DiscardedEdit(20_001, setOf("b"), SyncSide.LOCAL, 2L),
                ),
            )

            repository.dismissDiscardedEdits()

            repository.observeDiscardedEdits().test {
                assertEquals(emptyList<DiscardedEditNotice>(), awaitItem())
            }
        }

    private fun habit(
        id: String,
        name: String,
    ) = HabitEntity(
        id = id,
        notionPropertyId = id,
        notionPropertyName = name,
        color = "#4A9EFF",
        startDateEpochDay = 20_000,
        isActive = true,
        sortOrder = 0,
    )

    @Test
    fun askingForASyncGoesToTheScheduler() =
        runTest {
            // The "offer a retry" half of CLAUDE.md §3.5, and the only write this interface has.
            repository.requestSync()

            assertEquals(1, scheduler.syncRequests)
        }
}
