package com.cristianprincipi.habits.gallery

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.data.local.HabitsDatabase
import com.cristianprincipi.habits.data.local.entity.DayEntity
import com.cristianprincipi.habits.data.local.entity.HabitCompletionEntity
import com.cristianprincipi.habits.data.local.entity.HabitEntity
import com.cristianprincipi.habits.data.local.entity.SyncState
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.repository.NotionSetupRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Puts `SampleHabits` into the real database and opens the real shell on top of it — M9.8.
 *
 * ### Why this exists, and why it is not another preview activity
 *
 * The six preview activities beside it each render **one** screen over in-memory sample data. That
 * was enough for every step up to now, and it is not enough for M9.8: the clause M6 carried asks
 * somebody to check a habit off **on Week**, walk to **Stats**, and watch the current-streak tile
 * move. Two screens, one shared repository, real navigation between them — which is the shell, and
 * the shell reads Room.
 *
 * Room is empty until a Notion connection imports habits, and connecting costs the **user** a paste
 * of a token only they have (`CLAUDE.md` §3.6). So without this, the last step of M9 could only be
 * done by spending something of theirs.
 *
 * ### ⚠ It refuses to run when anything is connected, and that guard is the point
 *
 * [ShellHarnessActivity] is deliberately not exported because it hosts `SyncStatusScreen`, which can
 * disconnect a live connection — and its own KDoc's workaround is to edit the manifest and remember
 * to put it back. This is the better shape of the same protection: **it checks for a stored mapping
 * and stops**, which guards against the actual harm rather than against the launch.
 *
 * The harm is worth naming precisely, because it is not the reading. Seeded habits would sit beside
 * real ones until the next sync deactivated them — untidy but recoverable. What is *not*
 * recoverable is a check-off during the walk: `HabitRepositoryImpl.setCompleted` stamps
 * `PENDING_PUSH` unconditionally, so tapping a fake habit's pill on a connected build queues a write
 * against the user's real Notion workspace.
 *
 * ### What it seeds
 *
 * The five real habits from `docs/notion-schema.md`, with `SampleHabits`' generated history — the
 * same data every screenshot in M6 through M9 was reviewed against, so the walk looks like the
 * screens already discussed.
 *
 * **Today is deliberately left unchecked**, and one habit gets a clean run ending yesterday. Without
 * that the walk has nothing to tap: a generated history may or may not have done today, and a
 * current streak of zero shows nothing about a number that moves.
 *
 * ```
 * adb shell am start -n com.cristianprincipi.habits/com.cristianprincipi.habits.gallery.SeedActivity
 * ```
 */
@AndroidEntryPoint
class SeedActivity : ComponentActivity() {
    @Inject
    lateinit var database: HabitsDatabase

    @Inject
    lateinit var setup: NotionSetupRepository

    private var state by mutableStateOf<SeedState>(SeedState.Working)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Matching MainActivity rather than taking the default, for ADR-019's reason: the default
        // follows the system light/dark setting and would render dark-on-dark system bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            HabitsTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SeedReport(state)
                }
            }
        }

        lifecycleScope.launch {
            if (setup.isConfigured.first()) {
                state = SeedState.Refused
                return@launch
            }
            seed()
            // Same package, so `exported="false"` on the harness does not apply — that flag gates
            // other apps and the shell uid, not this one.
            startActivity(Intent(this@SeedActivity, ShellHarnessActivity::class.java))
            finish()
        }
    }

    private suspend fun seed() {
        val today = LocalDate.now()
        val habits = SampleHabits.habits(today)
        val history = walkableHistory(today)

        database.withTransaction {
            database.habitDao().upsert(habits.mapIndexed { index, habit -> habit.toRow(index) })
            history.map { it.date }.distinct().forEach { date -> database.dayDao().upsert(dayRow(date)) }
            history.forEach { completion ->
                database.completionDao().insert(
                    HabitCompletionEntity(completion.habitId.value, completion.date.toEpochDay()),
                )
            }
        }
    }

    /**
     * The generated history with **today removed**, plus a live run for the first habit.
     *
     * Both halves are what make the walk work rather than merely render. Today has to be open or
     * there is nothing to tap; and the generator's keep-rates will not reliably leave a run ending
     * yesterday, so `Read` is given one — the tile has to be showing a number before it can be seen
     * to change.
     */
    private fun walkableHistory(today: LocalDate): List<Completion> {
        val generated = SampleHabits.completions(today)
        val live = SampleHabits.unbrokenRun(id = LIVE_HABIT, days = LIVE_RUN, reference = today.minusDays(1))
        return (generated + live).filter { it.date != today }.distinct()
    }

    private fun Habit.toRow(index: Int) =
        HabitEntity(
            id = id.value,
            // Prefixed so a real sync can never mistake one of these for a property it owns.
            notionPropertyId = "seeded-${id.value}",
            notionPropertyName = "$emoji $name",
            color = color.name,
            startDateEpochDay = startDate.toEpochDay(),
            isActive = true,
            sortOrder = index,
        )

    /**
     * `SYNCED`, not `PENDING_PUSH`.
     *
     * This is history the app is pretending to have imported, and marking it owed would queue a push
     * for every day of it the moment anyone connected afterwards.
     */
    private fun dayRow(date: LocalDate) =
        DayEntity(
            epochDay = date.toEpochDay(),
            notionPageId = null,
            localUpdatedAtEpochMillis = null,
            remoteLastEditedAtEpochMillis = null,
            syncState = SyncState.SYNCED,
        )

    private companion object {
        const val LIVE_HABIT = "read"
        const val LIVE_RUN = 9
    }
}

private sealed interface SeedState {
    data object Working : SeedState

    data object Refused : SeedState
}

@Composable
private fun SeedReport(state: SeedState) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(Spacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
        ) {
            when (state) {
                SeedState.Working -> {
                    Text(
                        text = "Seeding sample habits…",
                        style = MaterialTheme.typography.titleMedium,
                        color = HabitsTheme.colors.text.primary,
                    )
                }

                SeedState.Refused -> {
                    Text(
                        text = "Not seeding — this app is connected to Notion",
                        style = MaterialTheme.typography.titleMedium,
                        color = HabitsTheme.colors.text.primary,
                    )
                    Text(
                        text =
                            "Sample habits would sit beside the real ones, and checking one off " +
                                "would queue a write against the real workspace. Disconnect first, " +
                                "or use the single-screen preview activities instead.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HabitsTheme.colors.text.secondary,
                    )
                }
            }
        }
    }
}
