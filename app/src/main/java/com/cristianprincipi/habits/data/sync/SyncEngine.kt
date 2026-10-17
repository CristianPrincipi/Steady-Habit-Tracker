package com.cristianprincipi.habits.data.sync

import com.cristianprincipi.habits.data.local.TransactionRunner
import com.cristianprincipi.habits.data.local.dao.CompletionDao
import com.cristianprincipi.habits.data.local.dao.DayDao
import com.cristianprincipi.habits.data.local.dao.MeasureDao
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.HabitCompletionEntity
import com.cristianprincipi.habits.data.local.entity.MeasurementEditEntity
import com.cristianprincipi.habits.data.local.entity.MeasurementEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.data.preferences.NotionDatabaseId
import com.cristianprincipi.habits.data.preferences.NotionMappingStore
import com.cristianprincipi.habits.data.preferences.NotionPropertyMapping
import com.cristianprincipi.habits.data.preferences.readablePropertyIds
import com.cristianprincipi.habits.data.remote.notion.NotionApi
import com.cristianprincipi.habits.data.remote.notion.NotionError
import com.cristianprincipi.habits.data.remote.notion.NotionResult
import com.cristianprincipi.habits.data.remote.notion.dto.NotionPageDto
import com.cristianprincipi.habits.data.remote.notion.mapper.NotionMappingCheck
import com.cristianprincipi.habits.data.remote.notion.mapper.NotionMappingValidator
import com.cristianprincipi.habits.data.remote.notion.mapper.NotionPropertyValue
import com.cristianprincipi.habits.data.remote.notion.mapper.property
import com.cristianprincipi.habits.domain.repository.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pull, push, reconcile. The one place local storage and Notion are made to agree.
 *
 * **Nothing on a screen ever waits for this.** `CLAUDE.md` §1: Room is the source of truth for the
 * UI, every user action succeeds locally and immediately, and this runs behind them. If it is broken
 * for a week the app keeps working and catches up afterwards.
 *
 * ### The order is load-bearing
 *
 * Validate → import habits → pull → push, and none of it may be reordered:
 *
 * 1. **Validate first** (`CLAUDE.md` §3.4). A property deleted or retyped upstream stops the run
 *    before anything is written, in either direction. Pushing under a stale mapping is how a real
 *    value gets overwritten with a null.
 * 2. **Pull before push**, which is what makes creating a page idempotent. A run killed between
 *    `createPage` returning and the page id being stored leaves an orphan page in Notion and a day
 *    that still believes it has none. The next pull matches that page to its day **by its `Date`
 *    property** and adopts it, so the push that follows updates instead of creating a second one.
 *    ⭐ **The date is the identity; [DayEntity.notionPageId] is only a cache of it.** Every recovery
 *    path in this class rests on that, including clearing a page id that turns out to be gone.
 * 3. **Push last**, so a day that both sides changed has already been marked [SyncState.CONFLICT]
 *    and is skipped — [DayDao.withState] asks for `PENDING_PUSH` only. Pushing a conflicted day
 *    would resolve it destructively and silently, which §3.5 forbids. The same holds for a number
 *    typed on this device (M16.2): the pull has already merged it, so what is still owed after the
 *    pull is exactly what should be sent.
 *
 * ### ⭐⭐ Two kinds of owed write since M16.2, and they never ride on each other (ADR-156)
 *
 * A day's **checkboxes** are owed when the day is `PENDING_PUSH` and decided per day by
 * [ConflictResolver]. A **number** is owed when it has a [MeasurementEditEntity] and is decided per
 * number, by a three-way merge against what Notion held when it was typed. One page update can carry
 * both, but neither is ever sent because of the other: a weight typed on the phone must not re-send
 * the phone's checkboxes over a habit ticked later in Notion, and a check-off must not re-send a
 * number nobody touched.
 *
 * ### Resumability is a property of the pull's sort order, not of a checkpoint
 *
 * There is no cursor stored anywhere. The resume point is `MAX(remoteLastEditedAtEpochMillis)` over
 * the days already stored, and that is only a *correct* resume point because the query asks for
 * results in ascending `last_edited_time` (`QueryDataSourceRequest.sorts`, `docs/notion-api.md`
 * §14). Every prefix of an ascending run is then a valid stopping point. Unsorted — which is what
 * Notion returns by default — a kill halfway through pagination would advance the watermark past
 * pages it never applied, and they would never be asked for again.
 *
 * ⭐ **The watermark is asked for inclusively (`on_or_after`), and it has to be.** Notion rounds
 * `last_edited_time` down to the minute (`notion-api.md` §7a, observed in M4.15). An exclusive
 * bound therefore skips not just the page the watermark came from but every *other* page edited in
 * that same minute — permanently, since the watermark never moves past them. Inclusive re-reads the
 * boundary minute on each run, which is cheap and harmless because applying a pulled day is
 * idempotent.
 *
 * ### A conflict is resolved where it is found
 *
 * A day both sides changed is decided by [ConflictResolver] during the pull, in the transaction
 * that discovered it — **nothing is left marked and stuck**. The page is in hand at that moment,
 * which is the whole reason: resolving later would mean fetching it again to find out what the
 * remote copy said. Remote wins and the page's check-offs are applied; local wins and the day goes
 * back to owing a push, which the phase below already knows how to do.
 *
 * Whatever the losing copy held is recorded as a [DiscardedEdit] before the run ends, because
 * `CLAUDE.md` §3.5 allows resolution to destroy an edit but not to do it quietly. Two cases never
 * reach the resolver at all, and both matter: if the two copies **agree**, there is nothing to
 * resolve; if **Notion has not moved** since we last looked, only this device changed and the day
 * simply still owes its push.
 *
 * ### What it does not do
 *
 * **It does not notice a day deleted in Notion.** `docs/notion-api.md` §14: a trashed page is never
 * returned by a query and an archived one is excluded by default, so a remote delete is discovered
 * by *absence*, which an incremental pull cannot see. What is handled instead is the moment it
 * matters — a push to a page that is gone — plus [NotionPageDto.isGone] on the off chance a flagged
 * page ever arrives.
 */
@Singleton
class SyncEngine
    @Inject
    // The collaborators this file needs to do what `CLAUDE.md` §5 gives it ("pull, push, reconcile"):
    // one remote, one mapping, the habit set, the two day-shaped tables, the transaction that spans
    // them, somewhere to put what resolution destroyed, and a clock. Hiding them behind a facade
    // would add an indirection whose only job is a lint count. `ConflictResolver` is absent from
    // this list on purpose — it is pure, so it is called rather than injected.
    @Suppress("LongParameterList")
    constructor(
        private val api: NotionApi,
        private val mappingStore: NotionMappingStore,
        private val habitImporter: HabitImporter,
        private val measureImporter: MeasureImporter,
        private val dayDao: DayDao,
        private val completionDao: CompletionDao,
        private val measureDao: MeasureDao,
        private val transactionRunner: TransactionRunner,
        private val discardedEdits: DiscardedEditStore,
        private val pullFormat: PullFormatStore,
        private val settings: SettingsRepository,
        private val clock: Clock,
    ) {
        /**
         * ⚠⚠ **One run at a time, per process — M14.10, and not optional.** Adoption by date makes a
         * *killed* run safe; it does nothing for two *live* ones. Both pull before either creates the
         * page, both find a day with no page, and Notion gets two pages for one date
         * (`SyncOverlapTest`). Three things can start a run — the periodic schedule, an on-demand or
         * app-open request, and the setup wizard's first import — and WorkManager only de-duplicates
         * within one unique name.
         *
         * Held here rather than in `SyncWorker` because the wizard calls this method directly. A
         * field on a `@Singleton` is enough: every worker runs in the app's own process.
         *
         * A second run **waits** rather than being skipped. What it was asked for — a pull after the
         * user edited Notion, a push of a check-off made during the first run — may not be in the
         * first run's results, and an incremental run after it costs a few requests.
         */
        private val oneRunAtATime = Mutex()

        /** One full reconciliation, after any run already in progress has finished. */
        suspend fun sync(): SyncOutcome = oneRunAtATime.withLock { reconcile() }

        /**
         * ReturnCount: there is one exit per phase that can stop the run, and each is the honest
         * end of the sync rather than a branch to fold away. Nesting them into a single expression
         * would bury the order this class's KDoc calls load-bearing.
         */
        @Suppress("ReturnCount")
        private suspend fun reconcile(): SyncOutcome {
            val mapping = mappingStore.read() ?: return SyncOutcome.NotConfigured

            val schema =
                when (val result = api.retrieveDataSource(mapping.dataSourceId.value)) {
                    is NotionResult.Failure -> return SyncOutcome.Failed(result.error)
                    is NotionResult.Success -> result.value
                }

            // ⭐ Where the app learns which database it is looking at — free, because this schema was
            // fetched to validate the mapping and carries its own parent (`NotionDataSourceDto`).
            //
            // ⚠ **Before the validation, deliberately.** A broken mapping is exactly when the user
            // needs the link out to Notion, since fixing it means going there — and the run below
            // returns at the next branch. Learning where the database is has nothing to do with
            // whether the columns still match.
            schema.databaseId?.let { id -> mappingStore.rememberDatabaseId(NotionDatabaseId(id)) }

            val valid =
                when (val check = NotionMappingValidator.validate(mapping, schema)) {
                    is NotionMappingCheck.Broken -> return SyncOutcome.MappingBroken(check.problems)
                    is NotionMappingCheck.Valid -> check
                }

            val habits = habitImporter.import(valid.habits)
            val measures = measureImporter.import(valid.measures)

            // Recorded before the outcome is known, and deliberately so: a discard that happened
            // is a fact whether or not the rest of the run succeeded, and a failed push must not
            // be the reason the user never hears about it.
            val discarded = mutableListOf<DiscardedEdit>()
            val pullResult = pull(mapping, habits, measures, discarded)
            discardedEdits.record(discarded)

            val pulled =
                when (pullResult) {
                    is NotionResult.Failure -> return SyncOutcome.Failed(pullResult.error)
                    is NotionResult.Success -> pullResult.value
                }

            habitImporter.refreshStartDates(habits)

            val pushed =
                when (val result = push(mapping, habits, measures)) {
                    is NotionResult.Failure -> return SyncOutcome.Failed(result.error)
                    is NotionResult.Success -> result.value
                }

            settings.setLastSyncedAt(clock.instant())
            return SyncOutcome.Success(
                daysPulled = pulled.applied,
                daysPushed = pushed,
                pagesSkipped = pulled.skipped,
            )
        }

        /** Days applied, and pages that could not be placed on one. */
        private data class PullReport(
            val applied: Int,
            val skipped: Int,
        )

        private suspend fun pull(
            mapping: NotionPropertyMapping,
            habits: MappedHabits,
            measures: MappedMeasures,
            discarded: MutableList<DiscardedEdit>,
        ): NotionResult<PullReport> {
            // ⚠⚠ **The watermark is skipped entirely when this build reads more of a page than
            // the one that filled Room did** (M15.6e, ADR-132). The incremental filter is right for
            // keeping up and wrong exactly once per new property: every page older than the
            // watermark is already "seen", so a field we have only just learned to read would never
            // be read for any of them. The user found this as a sparkline pinned to the right-hand
            // edge of its widget — two readings against a month that had never been fetched.
            // ⚠ Everything `applyPage` looks at, so the mapping gaining a column counts as
            // "this build reads more than the one that filled Room did" — which for the user is the
            // same event and the more likely one (`CLAUDE.md` §3.4: the mapping is user data).
            val readableIds = mapping.readablePropertyIds()
            val backfilling = pullFormat.needsBackfill(readableIds)
            // Read once, before the first request. The filter has to stay identical across a
            // paginated run, and it would otherwise move as this loop's own writes land.
            val editedAtOrAfter =
                if (backfilling) null else dayDao.latestRemoteEditMillis()?.let(Instant::ofEpochMilli)
            var cursor: String? = null
            var applied = 0
            var skipped = 0
            var truncated = false

            do {
                val query =
                    api.queryPages(
                        dataSourceId = mapping.dataSourceId.value,
                        editedAtOrAfter = editedAtOrAfter,
                        startCursor = cursor,
                    )
                val page =
                    when (query) {
                        is NotionResult.Failure -> return query
                        is NotionResult.Success -> query.value
                    }

                page.results.forEach { dto ->
                    if (applyPage(dto, mapping, habits, measures, discarded)) applied++ else skipped++
                }
                // `isTruncated` needs no branch of its own, and that is a consequence of the sort
                // rather than an oversight: at the 10,000 ceiling an ascending run has applied the
                // oldest-edited 10,000, the watermark now sits on the last of them, and the next
                // run continues from exactly there (`docs/notion-api.md` §5 and §14).
                truncated = truncated || page.isTruncated
                cursor = page.cursorForNextPage
            } while (cursor != null)

            // ⚠ Only after a pass that reached the end. A truncated backfill has read the oldest
            // 10,000 and has no watermark to resume from — recording it would strand the rest with
            // nothing to say so, where leaving it unrecorded simply tries again next run.
            if (backfilling && !truncated) pullFormat.recordBackfill(readableIds)

            return NotionResult.Success(PullReport(applied = applied, skipped = skipped))
        }

        /**
         * Reconciles one page into one day.
         *
         * ReturnCount: a page is unplaceable, gone, or a day — three outcomes, one exit each.
         *
         * @return false when the page could not be placed — no readable day key, or a timestamp
         *   that will not parse. Skipped rather than guessed at: a page with no date is not a day.
         */
        @Suppress("ReturnCount")
        private suspend fun applyPage(
            dto: NotionPageDto,
            mapping: NotionPropertyMapping,
            habits: MappedHabits,
            measures: MappedMeasures,
            discarded: MutableList<DiscardedEdit>,
        ): Boolean {
            val day = dto.property(mapping.dayKeyPropertyId.value) as? NotionPropertyValue.DateValue
            val remoteEditedAt = parseInstant(dto.lastEditedTime)
            if (day == null || remoteEditedAt == null) return false

            val epochDay = day.start.toEpochDay()
            if (dto.isGone) {
                reconcileGonePage(epochDay)
                return true
            }

            val remoteMillis = remoteEditedAt.toEpochMilli()
            val decided =
                transactionRunner.withTransaction {
                    val local = dayDao.find(epochDay)
                    val plan = planFor(local, remoteMillis, dto, epochDay, habits)

                    dayDao.upsert(
                        DayEntity(
                            epochDay = epochDay,
                            // Adopted unconditionally. This is the line that makes a killed
                            // `createPage` recoverable: whatever the content turns out to be, the
                            // page for this date is now known.
                            notionPageId = dto.id,
                            localUpdatedAtEpochMillis = local?.localUpdatedAtEpochMillis,
                            // Always advanced, in either state. It is the pull's watermark, so a
                            // day that kept its old value would be re-fetched forever.
                            remoteLastEditedAtEpochMillis = remoteMillis,
                            syncState = plan.state,
                        ),
                    )
                    if (plan.state == SyncState.SYNCED) applyCompletions(dto, epochDay, habits)
                    // ⭐⭐ **Not gated on the day's state, where the completions above are — and that
                    // is not an oversight of the gate.** That gate protects the day's *checkboxes*.
                    // A number has its own protection since M16.2: an edit row, merged one number
                    // at a time inside `applyMeasurements`. So a day that owes a check-off still
                    // gets today's weight from Notion, and a day that owes nothing for its
                    // checkboxes can still be owed a number.
                    listOfNotNull(plan.discarded) + applyMeasurements(dto, epochDay, remoteMillis, measures)
                }
            // Outside the transaction on purpose: a notice for a write that rolled back would be
            // an apology for something that never happened.
            discarded += decided
            return true
        }

        /**
         * What to do with one day, once both copies are known.
         *
         * @param discarded what resolution destroyed, or null when nothing was.
         */
        private data class DayPlan(
            val state: SyncState,
            val discarded: DiscardedEdit? = null,
        )

        /**
         * Decides one day, in four steps that get progressively less common.
         *
         * 1. **A day with nothing unpushed just takes the remote copy.** New, or already `SYNCED`:
         *    Notion is the only side that has moved.
         * 2. **If the two copies agree, there is nothing to resolve** — whatever the timestamps
         *    say. This is what stops a killed push from leaving a conflict behind: the page it
         *    created holds exactly what this device was trying to send.
         * 3. **If Notion has not moved since we last looked**, only this device has, so the day
         *    still owes its push. The remote value we would otherwise apply is the one we already
         *    have.
         * 4. **Otherwise both copies changed** and [ConflictResolver] decides by last-write-wins,
         *    naming what it destroys.
         *
         * A day found in `CONFLICT` is treated as unpushed and resolved here. Nothing writes that
         * state any more — it is the one M4.8 used before this step existed, and this is the door
         * out of it, so no day can be left stuck in it.
         */
        private suspend fun planFor(
            local: DayEntity?,
            remoteMillis: Long,
            dto: NotionPageDto,
            epochDay: Long,
            habits: MappedHabits,
        ): DayPlan {
            val unpushed = local?.syncState == SyncState.PENDING_PUSH || local?.syncState == SyncState.CONFLICT
            if (local == null || !unpushed) return DayPlan(SyncState.SYNCED)

            val remoteChecked = habits.checkedOn(dto)
            val localChecked = localCheckedOn(epochDay, habits)
            return when {
                localChecked == remoteChecked -> {
                    DayPlan(SyncState.SYNCED)
                }

                remoteMillis == local.remoteLastEditedAtEpochMillis -> {
                    DayPlan(SyncState.PENDING_PUSH)
                }

                else -> {
                    val resolution =
                        ConflictResolver.resolve(
                            localUpdatedAtEpochMillis = local.localUpdatedAtEpochMillis,
                            remoteEditedAtEpochMillis = remoteMillis,
                            localHabitIds = localChecked,
                            remoteHabitIds = remoteChecked,
                        )
                    DayPlan(
                        // Remote wins and the page is applied; local wins and the day goes back to
                        // owing a push, which the push phase sends in this same run.
                        state = if (resolution is Resolution.RemoteWins) SyncState.SYNCED else SyncState.PENDING_PUSH,
                        discarded =
                            DiscardedEdit(
                                epochDay = epochDay,
                                habitIds = resolution.discarded,
                                discardedSide = resolution.discardedSide,
                                resolvedAtEpochMillis = clock.millis(),
                            ),
                    )
                }
            }
        }

        /**
         * The habits this device records as done on a day, **over the mapped habits only**.
         *
         * A habit that has gone inactive still has its completions locally and has no property in
         * the mapping, so counting them would make every day disagree with Notion forever.
         */
        private suspend fun localCheckedOn(
            epochDay: Long,
            habits: MappedHabits,
        ): Set<String> {
            val mapped = habits.localIds.toSet()
            return completionDao
                .forDay(epochDay)
                .map(HabitCompletionEntity::habitId)
                .filter { it in mapped }
                .toSet()
        }

        /**
         * Writes the page's check-offs over the local ones, one mapped habit at a time.
         *
         * **Never a blanket delete of the day's completions.** A habit whose property has left the
         * mapping keeps its history (`HabitDao.deactivateAllExcept` deactivates rather than
         * deletes), and clearing the day wholesale would take that history with it.
         *
         * An **unreadable** value is left alone rather than read as `false`. A checkbox is never
         * null in Notion, so a value that will not decode means the shape changed — and deleting a
         * real completion over it would be inventing a miss. An **absent** property is different
         * and does mean not-done: the page predates the habit.
         */
        private suspend fun applyCompletions(
            dto: NotionPageDto,
            epochDay: Long,
            habits: MappedHabits,
        ) {
            habits.properties.forEach { mapped ->
                val habitId = habits.localIdOf(mapped) ?: return@forEach
                when (val value = dto.property(mapped.id.value)) {
                    is NotionPropertyValue.Checkbox -> {
                        if (value.checked) {
                            completionDao.insert(HabitCompletionEntity(habitId = habitId, epochDay = epochDay))
                        } else {
                            completionDao.delete(habitId = habitId, epochDay = epochDay)
                        }
                    }

                    null -> {
                        completionDao.delete(habitId = habitId, epochDay = epochDay)
                    }

                    else -> {
                        Unit
                    }
                }
            }
        }

        /**
         * Writes the page's numbers over the local ones, one mapped measure at a time — **except a
         * number this device still owes Notion, which is merged instead** (M16.2, [mergeReading]).
         *
         * [applyCompletions]' twin, with one case that has no counterpart there and one that reads
         * the same and means something different.
         *
         * **An [NotionPropertyValue.Empty] number is a day with no reading, and it must delete.**
         * Notion gives this its own case — *"the property is on the page but holds nothing"* — and
         * it is what a skipped weigh-in looks like. Storing `0.0` for it would drag the average
         * down with nothing on screen looking unwell, which is the one failure in this whole
         * feature that a user could not detect. An **absent** property means the same thing here:
         * the page predates the column.
         *
         * An **unreadable** value is left alone rather than deleted, exactly as a completion is —
         * and an owed edit is left alone with it. A value that will not decode means the shape
         * changed, and throwing away a real reading over it would be inventing a gap.
         *
         * @return what merging destroyed, one notice per number.
         */
        private suspend fun applyMeasurements(
            dto: NotionPageDto,
            epochDay: Long,
            remoteMillis: Long,
            measures: MappedMeasures,
        ): List<DiscardedEdit> =
            measures.properties.mapNotNull { mapped ->
                val measureId = measures.localIdOf(mapped) ?: return@mapNotNull null
                val remote =
                    when (val value = dto.property(mapped.id.value)) {
                        is NotionPropertyValue.Number -> value.value
                        NotionPropertyValue.Empty, null -> null
                        else -> return@mapNotNull null
                    }
                val owed = measureDao.findEdit(measureId = measureId, epochDay = epochDay)
                if (owed == null) {
                    applyReading(measureId, epochDay, remote)
                    null
                } else {
                    mergeReading(owed, remote, remoteMillis)
                }
            }

        /** A reading, or its absence: `null` deletes the row and never stores a zero. */
        private suspend fun applyReading(
            measureId: String,
            epochDay: Long,
            value: Double?,
        ) {
            if (value == null) {
                measureDao.deleteReading(measureId = measureId, epochDay = epochDay)
            } else {
                measureDao.upsertReading(MeasurementEntity(measureId = measureId, epochDay = epochDay, value = value))
            }
        }

        /**
         * One number this device owes, against what the page holds now — a three-way merge, with
         * [MeasurementEditEntity.baseValue] as the common ancestor (ADR-156 §2).
         *
         * 1. **Notion holds what we hold** → the two agree, and nothing is owed any more. This is
         *    also what stops a killed push from leaving an edit behind, as `planFor`'s step 2 does
         *    for a day.
         * 2. **Notion still holds the base** → it did not touch this number, so ours is still owed.
         * 3. **Otherwise both changed it**, and last-write-wins decides — the edit's time against
         *    the page's `last_edited_time`, ties to the device, as [ConflictResolver] does for a
         *    day. Local wins → still owed, now against the value it beat, so the next pull does not
         *    find the same conflict again. Remote wins → applied, nothing owed. Either way the loser
         *    is named, with both values.
         *
         * ⚠ The page's time is the **page's**, not the number's: a habit ticked in Notion after the
         * number was changed there makes the page newer. Notion keeps no per-property time, so this
         * is the same coarseness [ConflictResolver] documents, and the notice is the same remedy.
         */
        private suspend fun mergeReading(
            owed: MeasurementEditEntity,
            remote: Double?,
            remoteMillis: Long,
        ): DiscardedEdit? {
            val ours = measureDao.findReading(measureId = owed.measureId, epochDay = owed.epochDay)?.value
            return when {
                remote == ours -> {
                    measureDao.deleteEdit(measureId = owed.measureId, epochDay = owed.epochDay)
                    null
                }

                remote == owed.baseValue -> {
                    null
                }

                owed.editedAtEpochMillis >= remoteMillis -> {
                    measureDao.upsertEdit(owed.copy(baseValue = remote))
                    numberDiscarded(owed, SyncSide.REMOTE, discardedValue = remote, keptValue = ours)
                }

                else -> {
                    applyReading(owed.measureId, owed.epochDay, remote)
                    measureDao.deleteEdit(measureId = owed.measureId, epochDay = owed.epochDay)
                    numberDiscarded(owed, SyncSide.LOCAL, discardedValue = ours, keptValue = remote)
                }
            }
        }

        private fun numberDiscarded(
            owed: MeasurementEditEntity,
            side: SyncSide,
            discardedValue: Double?,
            keptValue: Double?,
        ): DiscardedEdit =
            DiscardedEdit(
                epochDay = owed.epochDay,
                habitIds = emptySet(),
                discardedSide = side,
                resolvedAtEpochMillis = clock.millis(),
                number =
                    DiscardedNumber(
                        measureId = owed.measureId,
                        discardedValue = discardedValue,
                        keptValue = keptValue,
                    ),
            )

        /**
         * A page Notion reports as trashed or archived.
         *
         * Rare to the point of theoretical — §14 says such a page is not returned by a query at all
         * — but the branch is short and the alternative is storing a day that upstream has deleted.
         *
         * **A day with an unpushed edit survives, and its page id is dropped so the push recreates
         * it** — and since M16.2 a number this device owes is an unpushed edit too. The day goes
         * back to `PENDING_PUSH` either way, so the new page carries the checkboxes as well as the
         * number: a page created with the number alone would come back on the next pull with every
         * habit unticked, and be applied over the phone's.
         *
         * This is the one conflict [ConflictResolver] does not decide, because it cannot: a
         * deletion carries no timestamp to compare, so last-write-wins has nothing to weigh. Local
         * wins by construction instead, and the reasoning is asymmetric on purpose — this app has
         * no way to delete in Notion (ADR-036), so honouring the deletion would destroy the local
         * check-off with no way for the user to get it back, while ignoring it costs them one page
         * they can delete again.
         */
        private suspend fun reconcileGonePage(epochDay: Long) {
            transactionRunner.withTransaction {
                val local = dayDao.find(epochDay) ?: return@withTransaction
                val owesNothing = local.syncState == SyncState.SYNCED && measureDao.editsForDay(epochDay).isEmpty()
                if (owesNothing) {
                    dayDao.delete(epochDay)
                } else {
                    dayDao.upsert(local.copy(notionPageId = null, syncState = SyncState.PENDING_PUSH))
                }
            }
        }

        /**
         * Sends every day that owes Notion a write, oldest first.
         *
         * **Two sources since M16.2**: the days whose checkboxes are `PENDING_PUSH`, and the days
         * holding a number this device owes. A number owed for a measure the mapping no longer
         * names is **kept and not sent** — there is no validated property to send it to, and
         * writing under a stale mapping is what `CLAUDE.md` §3.4 forbids. It goes out the day the
         * column is mapped again.
         *
         * **One page update per day, not one per check-off.** `CLAUDE.md` §3.5 asks for writes to be
         * batched and coalesced; here it falls out of the data — five habits and two numbers on a
         * day are seven properties of one page.
         *
         * The first failure stops the run and the remaining days stay owed. Pressing on would mean
         * spending the rate limit re-learning the same 429 or the same dead socket once per day.
         */
        private suspend fun push(
            mapping: NotionPropertyMapping,
            habits: MappedHabits,
            measures: MappedMeasures,
        ): NotionResult<Int> {
            val mappedMeasures = measures.localIds.toSet()
            val owedNumbers =
                measureDao
                    .allEdits()
                    .filter { edit -> edit.measureId in mappedMeasures }
                    .groupBy(MeasurementEditEntity::epochDay)
            val days =
                (dayDao.withState(SyncState.PENDING_PUSH) + owedNumbers.keys.mapNotNull { dayDao.find(it) })
                    .distinctBy(DayEntity::epochDay)
                    .sortedBy(DayEntity::epochDay)

            var pushed = 0
            days.forEach { day ->
                when (val result = pushDay(day, mapping, habits, measures, owedNumbers[day.epochDay].orEmpty())) {
                    is NotionResult.Failure -> return result
                    is NotionResult.Success -> pushed++
                }
            }
            return NotionResult.Success(pushed)
        }

        /** One owed number as it went into a request body — what [recordPush] settles it against. */
        private data class SentNumber(
            val edit: MeasurementEditEntity,
            val propertyId: String,
            val value: Double?,
        )

        /**
         * One day, as one request.
         *
         * **Only what is owed is sent**, so `Area`, `Progress` and every number nobody typed on this
         * device come through a sync untouched — `docs/notion-schema.md` §7 requires exactly that,
         * and `updatePage` being a partial update is what delivers it.
         *
         * - **The checkboxes only when the day is `PENDING_PUSH`** — and then every mapped habit,
         *   unchecked ones included. Un-checking is a checkbox going to `false`, not a value being
         *   omitted (ADR-036: nothing here deletes), and a habit left out would keep whatever Notion
         *   had. ⚠ A day sent for a number alone sends **no** checkbox: they are not owed, and
         *   sending the phone's copy would overwrite a habit ticked in Notion since (ADR-156).
         * - **Each owed number as it stands in Room now**: its value, or an emptied number when it
         *   was cleared — never a zero.
         */
        private suspend fun pushDay(
            day: DayEntity,
            mapping: NotionPropertyMapping,
            habits: MappedHabits,
            measures: MappedMeasures,
            owed: List<MeasurementEditEntity>,
        ): NotionResult<Unit> {
            val checkboxes =
                if (day.syncState == SyncState.PENDING_PUSH) {
                    val checked = completionDao.forDay(day.epochDay).map(HabitCompletionEntity::habitId).toSet()
                    habits.properties.associate { mapped ->
                        mapped.id.value to NotionPropertyValue.Checkbox(habits.localIdOf(mapped) in checked).toWire()
                    }
                } else {
                    emptyMap()
                }
            val sent =
                owed.mapNotNull { edit ->
                    val propertyId = measures.propertyIdOf(edit.measureId) ?: return@mapNotNull null
                    val value = measureDao.findReading(measureId = edit.measureId, epochDay = edit.epochDay)?.value
                    SentNumber(edit = edit, propertyId = propertyId, value = value)
                }
            val properties =
                checkboxes +
                    sent.associate { number ->
                        number.propertyId to
                            NotionPropertyValue.numberOrCleared(number.value).toWire()
                    }

            val result =
                if (day.notionPageId == null) {
                    val date = NotionPropertyValue.DateValue(LocalDate.ofEpochDay(day.epochDay))
                    api.createPage(
                        dataSourceId = mapping.dataSourceId.value,
                        properties = properties + (mapping.dayKeyPropertyId.value to date.toWire()),
                    )
                } else {
                    api.updatePage(pageId = day.notionPageId, properties = properties)
                }

            return when (result) {
                is NotionResult.Success -> {
                    recordPush(day, result.value, sent)
                    NotionResult.Success(Unit)
                }

                is NotionResult.Failure -> {
                    if (result.error is NotionError.MissingOrNotShared && day.notionPageId != null) {
                        // The page is gone, or access to it is. Either way the id is worthless, and
                        // it is only ever a cache of the date: forget it, keep the day owed, and let
                        // the next run either re-adopt the page on pull or create a new one. This is
                        // the only remote delete this app can actually detect (`notion-api.md` §14).
                        // ⚠ Owed **in full** — `PENDING_PUSH` even for a day sent for a number alone,
                        // for `reconcileGonePage`'s reason: a new page must carry the checkboxes too.
                        dayDao.find(day.epochDay)?.let { current ->
                            dayDao.upsert(current.copy(notionPageId = null, syncState = SyncState.PENDING_PUSH))
                        }
                    }
                    result
                }
            }
        }

        /**
         * Marks a day pushed — **unless the user changed it while the request was in flight.**
         *
         * A check-off during the round trip is not hypothetical on a device: the write lands in Room
         * immediately, `PENDING_PUSH` is stamped again, and a blind `SYNCED` here would erase the
         * only record that Notion is behind. So the day is re-read and its
         * [DayEntity.localUpdatedAtEpochMillis] compared with the value this push was built from; if
         * it moved, the state is left exactly as it is and the next run sends the newer version.
         * A day sent for a number alone sent no checkboxes, so its state is left as it is too.
         *
         * **Each number sent is settled by its value, not its time** (M16.2): if Room still holds
         * what was sent, Notion holds it now and nothing is owed. If the user typed again in flight,
         * the edit stays owed with the sent value as its base — which is what Notion now holds, so
         * the next pull reads it as untouched rather than as a conflict.
         *
         * The page id and the remote timestamp are stored either way. They are Notion's facts, they
         * are true whoever won the race, and recording the timestamp is what stops the next pull
         * treating this app's own write as a remote change.
         */
        private suspend fun recordPush(
            day: DayEntity,
            page: NotionPageDto,
            sent: List<SentNumber>,
        ) {
            transactionRunner.withTransaction {
                val current = dayDao.find(day.epochDay) ?: return@withTransaction
                val checkboxesSent = day.syncState == SyncState.PENDING_PUSH
                val editedWhilePushing = current.localUpdatedAtEpochMillis != day.localUpdatedAtEpochMillis
                dayDao.upsert(
                    current.copy(
                        notionPageId = page.id,
                        remoteLastEditedAtEpochMillis =
                            parseInstant(page.lastEditedTime)?.toEpochMilli()
                                ?: current.remoteLastEditedAtEpochMillis,
                        syncState = if (checkboxesSent && !editedWhilePushing) SyncState.SYNCED else current.syncState,
                    ),
                )
                sent.forEach { number -> settle(number) }
            }
        }

        private suspend fun settle(number: SentNumber) {
            val edit = number.edit
            val owed = measureDao.findEdit(measureId = edit.measureId, epochDay = edit.epochDay) ?: return
            val held = measureDao.findReading(measureId = edit.measureId, epochDay = edit.epochDay)?.value
            if (held == number.value) {
                measureDao.deleteEdit(measureId = edit.measureId, epochDay = edit.epochDay)
            } else {
                measureDao.upsertEdit(owed.copy(baseValue = number.value))
            }
        }
    }

/**
 * Notion's timestamps are ISO-8601 in UTC (`2026-08-19T10:00:00.000Z`).
 *
 * `null` rather than an exception, for the same reason the property codec never throws (ADR-044):
 * one page with a timestamp this build cannot read must not take the rest of the pull with it.
 */
private fun parseInstant(raw: String): Instant? = runCatching { Instant.parse(raw) }.getOrNull()
