package com.cristianprincipi.habits.data.sync

/**
 * Decides a day that both sides changed. **Last write wins, per day** (`CLAUDE.md` §3.5).
 *
 * Pure and total: it reads two timestamps and two sets of habit ids and answers. No clock, no
 * storage, no network — which is what lets every case below be a three-line test rather than a
 * scenario.
 *
 * ### Per day, not per habit
 *
 * A day is one Notion page, and the only timestamps either side keeps are for the whole page:
 * `DayEntity.localUpdatedAtEpochMillis` and Notion's `last_edited_time`. Resolving habit by habit
 * would need a per-habit edit time that neither side stores, so it would come down to inventing one
 * — and an invented timestamp resolving a real conflict is worse than a coarse rule that is honest
 * about being coarse.
 *
 * ### ⚠ The two timestamps come from two different clocks
 *
 * `localUpdatedAtEpochMillis` is the **device's** clock; the remote one is **Notion's**. Comparing
 * them is comparing unsynchronised clocks, so a device running ten minutes fast wins every conflict
 * it is party to. `CLAUDE.md` §3.5 specifies this comparison and this is the faithful
 * implementation of it, but the wart is real and is written down here rather than discovered later.
 * Nothing available to a single-user app with no backend fixes it: there is no third clock to
 * appeal to, and Notion exposes no vector clock or revision number to compare instead.
 *
 * The mitigation is the one the constitution already asks for — **say what was discarded**
 * ([Resolution.discarded]) — so a wrong call is visible rather than silent.
 */
object ConflictResolver {
    /**
     * @param localUpdatedAtEpochMillis when this device last edited the day, or null if it has no
     *   record of ever having done so — in which case there is no local edit to defend and the
     *   remote copy wins by default.
     * @param localHabitIds the habits this device records as done that day, as local ids.
     * @param remoteHabitIds the habits the Notion page records as done that day, same ids.
     */
    fun resolve(
        localUpdatedAtEpochMillis: Long?,
        remoteEditedAtEpochMillis: Long,
        localHabitIds: Set<String>,
        remoteHabitIds: Set<String>,
    ): Resolution {
        // The habits the two sides disagree about — and therefore exactly the values the loser's
        // copy holds that the winner's does not. A habit both sides agree on is not discarded by
        // either outcome, so naming it in the notice would be telling the user they lost something
        // they still have.
        val contested = (localHabitIds - remoteHabitIds) + (remoteHabitIds - localHabitIds)

        // Ties go to the device. Two unsynchronised clocks agreeing to the millisecond is a
        // coincidence rather than a signal, so the tiebreak is a judgement call either way: this
        // one keeps the edit made on the device in the user's hand, and costs one page update.
        val localWins = localUpdatedAtEpochMillis != null && localUpdatedAtEpochMillis >= remoteEditedAtEpochMillis
        return if (localWins) Resolution.LocalWins(contested) else Resolution.RemoteWins(contested)
    }
}

/**
 * Which copy of a day survived, and what the other one was holding.
 *
 * [discarded] is never used to *decide* anything — it exists so the run can say what it did
 * (`CLAUDE.md` §3.5: "where resolution discards a user's edit, say so in the UI. Never resolve
 * destructively and silently"). It is empty when the two sides disagreed about nothing, which
 * happens when a conflict is decided over habits both copies already agreed on.
 */
sealed interface Resolution {
    val discarded: Set<String>

    /** Which copy lost. The inverse of which one won, and the half the user needs to be told. */
    val discardedSide: SyncSide

    /**
     * Notion's copy is newer. The page's check-offs are applied over the local ones and the day
     * becomes `SYNCED`; the local edit in [discarded] is gone.
     */
    data class RemoteWins(
        override val discarded: Set<String>,
    ) : Resolution {
        override val discardedSide = SyncSide.LOCAL
    }

    /**
     * This device's copy is newer.
     *
     * The day keeps its completions and goes back to owing a push — resolution never writes to
     * Notion itself, because the push phase already knows how to do that and how to survive being
     * killed part-way through it.
     */
    data class LocalWins(
        override val discarded: Set<String>,
    ) : Resolution {
        override val discardedSide = SyncSide.REMOTE
    }
}
