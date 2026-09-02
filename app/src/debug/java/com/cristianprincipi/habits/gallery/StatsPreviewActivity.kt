package com.cristianprincipi.habits.gallery

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.feature.stats.StatsContent
import com.cristianprincipi.habits.feature.stats.StatsEvent
import com.cristianprincipi.habits.feature.stats.StatsUiState

/**
 * The Stats screen against `SampleHabits`, with no Room and no Notion behind it (M9.1, M9.2).
 *
 * The same reason and the same safety as [WeekPreviewActivity], [MonthPreviewActivity] and
 * [YearPreviewActivity]: the screen shows nothing until a Notion connection has imported habits,
 * and connecting costs the **user** a paste of a token only they have. Nothing here touches a
 * repository.
 *
 * ⭐ **The pills work here** (M9.2). Selection is a `mutableStateOf` and the figures are recomputed
 * from `SampleHabits` under it, which is what makes the one interaction on this screen watchable
 * before there is a ViewModel behind it — the same trade `WeekPreviewActivity` makes for check-off.
 *
 * ⚠ **It had a `--es numerals mono` switch and no longer does.** That existed to settle ADR-021,
 * which the user closed at M9.8 in favour of the design's DM Sans; the enum went with the answer.
 *
 * The first habit's history is `completions() + unbrokenRun()`, so its current streak is alive. The
 * generated history alone will not reliably contain a run ending today, and a screen whose headline
 * number is "current streak: 0" shows nothing about how the number looks.
 *
 * ```
 * adb shell am start -n com.cristianprincipi.habits/com.cristianprincipi.habits.gallery.StatsPreviewActivity
 * adb shell am start -S -n …/….gallery.StatsPreviewActivity --es state pending
 * adb shell am start -S -n …/….gallery.StatsPreviewActivity --es state failed
 * adb shell am start -S -n …/….gallery.StatsPreviewActivity --ei age 7
 * ```
 *
 * ⭐ **`--ei age` is M9.6's, and it exists because the question that step opens is not answerable
 * from prose.** Every section of this screen is *drawable* for a habit one week old — a year grid
 * that is almost entirely "before this existed", four zeros, one monthly bucket drawn as a dot —
 * and `StatsUiState`'s KDoc argues each of those is a true thing to show. Whether the result reads
 * as a finished screen or as one that failed to load is a question for the emulator. Nothing in
 * `SampleHabits` could ask it: its habits take their start dates from the reference date, so the
 * youngest is always 95 days old.
 *
 * ⚠ `-S` matters when the activity is already up: `am start` compares intents with
 * `filterEquals`, which **ignores extras**, so without it a second launch resumes the old instance
 * and the state extra is silently dropped.
 */
class StatsPreviewActivity : ComponentActivity() {
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
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SampleStats(
                        empty = emptyReasonFrom(intent.getStringExtra(EXTRA_STATE)),
                        ageDays = intent.getIntExtra(EXTRA_AGE, FULL_HISTORY),
                    )
                }
            }
        }
    }
}

private const val EXTRA_STATE = "state"
private const val EXTRA_AGE = "age"

/** No trimming: every habit keeps the history `SampleHabits` generated for it. */
private const val FULL_HISTORY = 0

/** `null` for the sample habit; a reason to render [StatsUiState.Empty] instead. */
private fun emptyReasonFrom(extra: String?): HabitsEmptyReason? =
    when (extra) {
        "pending" -> HabitsEmptyReason.SyncPending
        "failed" -> HabitsEmptyReason.SyncFailed
        else -> null
    }

/**
 * The real [StatsContent] over `SampleHabits`, with the selection held locally.
 *
 * ⭐ **The pills genuinely switch the screen here**, because the figures are recomputed from
 * `SampleHabits` under the chosen id rather than fixed at composition. That is the whole of what
 * M9.2 adds, and the only thing on this screen that can be got wrong by watching it.
 *
 * ⚠ **Only `read` carries [SampleHabits.unbrokenRun]**, so exactly one habit shows a live current
 * streak and the rest show whatever the generator gave them. Giving every habit a run would hide
 * the "current streak: 0" case, which M9.4's tiles have to look right in too.
 *
 * It takes its own `safeDrawing` insets because there is no shell around it here (ADR-063's rule
 * applies inside the graph; this is outside it).
 */
@Composable
private fun SampleStats(
    empty: HabitsEmptyReason?,
    ageDays: Int,
) {
    val full = SampleHabits.habits()
    val (habits, history) =
        remember(ageDays) {
            agedTo(ageDays, full, SampleHabits.completions() + SampleHabits.unbrokenRun())
        }
    var selectedId by remember { mutableStateOf(full.first().id) }

    StatsContent(
        state =
            empty?.let(StatsUiState::Empty)
                ?: StatsUiState.Content(
                    habits = habits,
                    selected =
                        SampleHabits.stats(
                            id = selectedId.value,
                            completions = history,
                            habits = habits,
                        ),
                ),
        onEvent = { event -> if (event is StatsEvent.HabitSelected) selectedId = event.habitId },
        // Real navigation is the shell's; here it is the one thing this activity cannot show.
        onOpenSyncStatus = {},
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}

/**
 * The sample set as it would look [ageDays] after the user first connected — M9.6's knob.
 *
 * Moves every start date forward to `today − (ageDays − 1)` and drops the completions behind it, so
 * a habit reads as one that has only just arrived from Notion. `maxOf` rather than a plain
 * assignment: a habit already younger than that keeps its own date rather than being aged
 * *backwards* into a history it does not have.
 *
 * `FULL_HISTORY` returns the pair untouched, which is what every other launch of this activity gets.
 */
private fun agedTo(
    ageDays: Int,
    habits: List<Habit>,
    history: List<Completion>,
): Pair<List<Habit>, List<Completion>> {
    if (ageDays <= FULL_HISTORY) return habits to history
    val start = SampleHabits.referenceDate.minusDays((ageDays - 1).toLong())
    return habits.map { habit -> habit.copy(startDate = maxOf(habit.startDate, start)) } to
        history.filter { completion -> completion.date >= start }
}
