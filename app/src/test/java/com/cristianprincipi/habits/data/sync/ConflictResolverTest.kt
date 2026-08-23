package com.cristianprincipi.habits.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Last-write-wins, in isolation.
 *
 * The resolver is pure, so the whole rule fits in a handful of three-line tests rather than in sync
 * scenarios — which matters because this is the component that is allowed to destroy a user's edit.
 * The wiring is tested in `SyncEngineTest`; what is decided is tested here.
 */
class ConflictResolverTest {
    private val read = "habit-read"
    private val journal = "habit-journal"

    @Test
    fun theNewerCopyWinsWhenItIsNotions() {
        val resolution =
            ConflictResolver.resolve(
                localUpdatedAtEpochMillis = 1_000,
                remoteEditedAtEpochMillis = 2_000,
                localHabitIds = setOf(read),
                remoteHabitIds = emptySet(),
            )

        assertEquals(Resolution.RemoteWins(discarded = setOf(read)), resolution)
    }

    @Test
    fun theNewerCopyWinsWhenItIsThisDevices() {
        val resolution =
            ConflictResolver.resolve(
                localUpdatedAtEpochMillis = 3_000,
                remoteEditedAtEpochMillis = 2_000,
                localHabitIds = setOf(read),
                remoteHabitIds = emptySet(),
            )

        assertEquals(Resolution.LocalWins(discarded = setOf(read)), resolution)
    }

    @Test
    fun aTieGoesToTheDevice() {
        // Two unsynchronised clocks agreeing to the millisecond is coincidence, not signal, so the
        // tiebreak is a judgement either way. This one keeps the edit made in the user's hand.
        val resolution =
            ConflictResolver.resolve(
                localUpdatedAtEpochMillis = 2_000,
                remoteEditedAtEpochMillis = 2_000,
                localHabitIds = setOf(read),
                remoteHabitIds = emptySet(),
            )

        assertEquals(Resolution.LocalWins(discarded = setOf(read)), resolution)
    }

    @Test
    fun aDeviceWithNoRecordOfEditingTheDayHasNothingToDefend() {
        // Nothing can produce this today — a day only reaches the resolver by being unpushed, and
        // becoming unpushed stamps the time. It is answered anyway rather than left to a `!!`.
        val resolution =
            ConflictResolver.resolve(
                localUpdatedAtEpochMillis = null,
                remoteEditedAtEpochMillis = 1,
                localHabitIds = setOf(read),
                remoteHabitIds = emptySet(),
            )

        assertEquals(Resolution.RemoteWins(discarded = setOf(read)), resolution)
    }

    @Test
    fun whatIsDiscardedIsWhatTheTwoCopiesDisagreeAbout() {
        // Both directions: a habit only this device has, and a habit only Notion has. Either way it
        // is a value the loser held and the winner does not.
        val resolution =
            ConflictResolver.resolve(
                localUpdatedAtEpochMillis = 9_000,
                remoteEditedAtEpochMillis = 1_000,
                localHabitIds = setOf(read),
                remoteHabitIds = setOf(journal),
            )

        assertEquals(setOf(read, journal), resolution.discarded)
    }

    @Test
    fun aHabitBothCopiesAgreeOnIsNeverReportedAsDiscarded() {
        // Telling the user they lost something they still have is its own kind of lying.
        val resolution =
            ConflictResolver.resolve(
                localUpdatedAtEpochMillis = 9_000,
                remoteEditedAtEpochMillis = 1_000,
                localHabitIds = setOf(read, journal),
                remoteHabitIds = setOf(read),
            )

        assertEquals(setOf(journal), resolution.discarded)
    }

    @Test
    fun copiesThatAgreeEntirelyDiscardNothing() {
        val resolution =
            ConflictResolver.resolve(
                localUpdatedAtEpochMillis = 9_000,
                remoteEditedAtEpochMillis = 1_000,
                localHabitIds = setOf(read),
                remoteHabitIds = setOf(read),
            )

        assertEquals(emptySet<String>(), resolution.discarded)
    }
}
