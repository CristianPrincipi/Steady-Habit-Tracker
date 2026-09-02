package com.cristianprincipi.habits.data.local.dao

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.testing.createInMemoryDatabase
import com.cristianprincipi.habits.data.local.HabitsDatabase
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * What SQLite actually does with the query every screen in the app runs — `Plan.md` M9.7.
 *
 * ### Why this is a query plan and not a stopwatch
 *
 * M9.7 asks for "not a full table scan". That is not a matter of opinion or of how fast a test
 * happens to run on one machine: SQLite will say, in words, whether it is scanning a table or
 * searching an index. `EXPLAIN QUERY PLAN` returns `SEARCH … USING INDEX …` for the one and
 * `SCAN …` for the other, and the difference is the whole of what the step is asking about.
 *
 * ### ⚠ The mutant, and it is a real one
 *
 * Delete `indices = [Index(value = ["epochDay"])]` from `HabitCompletionEntity` and **every test in
 * the suite still passes** — the query returns identical rows, just by reading every one of them.
 * Nothing else in this project would notice. That is exactly the shape of hole ADR-089 found and
 * ADR-088 §3 warned about, and it is why this file exists.
 *
 * ### And the control, because an assertion that cannot fail is worse than none
 *
 * [theSameAssertionCanSeeATableScan] runs a deliberately un-indexable predicate and requires the
 * plan to come back a `SCAN`. Without it, a typo in the expected wording — or a future SQLite that
 * phrases plans differently — would leave this file green and blind. ADR-088 §3 caught a vacuous
 * assertion one milestone after it was written; this one is checked in the same commit that makes
 * the claim.
 *
 * ### Every plan is logged, and reading them corrected this file once already
 *
 * `adb logcat -s M9.7` after a run prints each query beside its plan. That is not decoration:
 * [theSyncPathsPlansAreRecordedRatherThanRequired] was written predicting `SCAN` for four queries
 * and the log showed two of them come back `SEARCH`. A green test would have carried the wrong
 * comment forward for ever.
 */
@RunWith(AndroidJUnit4::class)
class QueryPlanTest {
    private lateinit var database: HabitsDatabase

    @Before
    fun setUp() {
        database = createInMemoryDatabase()
    }

    @After
    fun tearDown() = database.close()

    /** Every line SQLite reports for [sql], which for these queries is one. */
    private fun planFor(
        sql: String,
        vararg args: Any,
    ): String {
        val cursor = database.openHelper.readableDatabase.query("EXPLAIN QUERY PLAN $sql", args)
        return cursor
            .use { rows ->
                val detail = rows.getColumnIndexOrThrow("detail")
                generateSequence { if (rows.moveToNext()) rows.getString(detail) else null }.joinToString("; ")
            }.also { plan -> Log.i(TAG, "$sql  ==>  $plan") }
    }

    @Test
    fun theQueryEveryScreenRunsSearchesTheIndexRatherThanScanningTheTable() {
        // ⭐ This is the hot path of the whole app. Week, Month, Year and Stats all reach it through
        // `HabitRepository.observeCompletions`, it re-runs on every check-off and every sync, and it
        // is the only query in the project whose table grows without bound — one row per habit per
        // day kept, for ever.
        val plan = planFor("SELECT * FROM habit_completions WHERE epochDay BETWEEN ? AND ?", 0, 365)

        assertTrue("Not an indexed search: $plan", plan.startsWith("SEARCH"))
        assertTrue("Not using the epochDay index: $plan", plan.contains("index_habit_completions_epochDay"))
    }

    @Test
    fun theSameAssertionCanSeeATableScan() {
        // ⚠⚠ The control. `epochDay + 0` is arithmetically the same predicate and structurally
        // un-indexable, so SQLite has no choice but to read every row — and it will keep having no
        // choice, which is what makes this a stable control rather than a change detector pointed at
        // an index somebody might add later.
        val plan = planFor("SELECT * FROM habit_completions WHERE epochDay + 0 BETWEEN ? AND ?", 0, 365)

        assertTrue("A scan did not report as one, so the test above proves nothing: $plan", plan.startsWith("SCAN"))
    }

    @Test
    fun theStartDateLookupIsAnIndexedAggregate() {
        // `earliestDayFor` is the one aggregate this app does compute in SQL, and it is how a habit
        // gets a start date at all (ADR-046). `habitId` leads the composite primary key, so SQLite
        // has an automatic index for it and never reads a row it does not need.
        val plan = planFor("SELECT MIN(epochDay) FROM habit_completions WHERE habitId = ?", "read")

        assertTrue("The start-date lookup scans the table: $plan", plan.startsWith("SEARCH"))
    }

    @Test
    fun theSyncPathsPlansAreRecordedRatherThanRequired() {
        // ⚠⚠ **This test's own comment used to predict `SCAN` for all four, and the run disproved
        // it.** `days` carries no declared index, so "therefore every query over it scans" looked
        // obvious. What SQLite actually reports, measured on the device:
        //
        //   SELECT MAX(remoteLastEditedAtEpochMillis) FROM days  ->  SEARCH days
        //   SELECT MIN(epochDay) FROM days                       ->  SEARCH days
        //   SELECT * FROM days WHERE syncState = ?               ->  SCAN days
        //   SELECT COUNT(*) FROM days                            ->  SCAN days
        //
        // `MIN(epochDay)` is explicable: `epochDay` is the primary key of a rowid table, so the
        // minimum is the first row and SQLite seeks straight to it. **The `MAX` label is not
        // explicable from this schema** — that column carries no index — and it is left recorded
        // rather than explained, because inventing a reason is how a wrong one gets inherited.
        //
        // What it does not change: both paths run **once per sync**, every 15–30 minutes, over a
        // table that grows by one row per day. Neither is worth an index that every write would pay
        // for. The hot path is the other test in this file.
        //
        // The assertion is deliberately the weak one — that a plan exists at all. Requiring `SCAN`
        // would be a change detector aimed at an improvement (ADR-090's lesson), and requiring
        // `SEARCH` would pin wording nobody here understands.
        val plans =
            listOf(
                planFor("SELECT MAX(remoteLastEditedAtEpochMillis) FROM days"),
                planFor("SELECT * FROM days WHERE syncState = ?", "PENDING_PUSH"),
                planFor("SELECT MIN(epochDay) FROM days"),
                planFor("SELECT COUNT(*) FROM days"),
            )

        assertTrue("A sync-path query produced no plan at all: $plans", plans.all { it.isNotEmpty() })
    }

    private companion object {
        /** Logged so the plans can be read off a run rather than taken on trust. `adb logcat -s M9.7`. */
        const val TAG = "M9.7"
    }
}
