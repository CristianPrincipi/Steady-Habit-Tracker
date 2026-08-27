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
import com.cristianprincipi.habits.core.datetime.DAYS_PER_WEEK
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.Completion
import com.cristianprincipi.habits.feature.week.WeekContent
import com.cristianprincipi.habits.feature.week.WeekEvent
import com.cristianprincipi.habits.feature.week.WeekUiState

/**
 * The Week screen against `SampleHabits`, with no Room and no Notion behind it.
 *
 * ### Why this exists
 *
 * `WeekScreen` only shows habits once a Notion connection has imported some, and connecting costs
 * the **user** a paste of a token only they have (`CLAUDE.md` §7's warning about the gate run). That
 * made the app's most important screen one that could not be looked at during the milestone that
 * builds it — and `CLAUDE.md` §7 is emphatic that a rendering bug you can see is one you can fix in
 * the same turn. `SampleHabits` already carries five habits with a year of realistic history, so
 * this wires that straight into the real `WeekContent`.
 *
 * It is also how M6.4, M6.5 and M6.8 get looked at: paging, check-off and the empty state are all
 * reachable here without any data at all.
 *
 * ### Exported, unlike [ShellHarnessActivity], and safe to be
 *
 * That one is closed because it hosts the real `SyncStatusScreen`, which can disconnect a live
 * workspace. This one holds nothing but sample data in memory: the toggle below edits a local
 * `mutableStateOf` and no repository, so there is nothing here to damage. Debug source set only, so
 * it cannot reach a release build.
 *
 * ```
 * adb shell am start -n com.cristianprincipi.habits/com.cristianprincipi.habits.gallery.WeekPreviewActivity
 * ```
 */
class WeekPreviewActivity : ComponentActivity() {
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
                    SampleWeek()
                }
            }
        }
    }
}

/**
 * `WeekContent` over an in-memory week that a tap actually changes.
 *
 * The toggle is real — it rebuilds the state the way the ViewModel would — so what this shows is not
 * a static mock but the whole loop minus Room. It takes its own `safeDrawing` insets because there
 * is no shell around it here (ADR-063's rule applies inside the graph; this is outside it).
 */
@Composable
private fun SampleWeek() {
    val reference = SampleHabits.referenceDate
    val thisWeek = startOfWeek(reference)

    var completed by remember { mutableStateOf(SampleHabits.completions(reference).toSet()) }
    var weekStart by remember { mutableStateOf(thisWeek) }

    // The same shape `WeekViewModel` produces, minus Room: `weekPeriods` is anchored on the shown
    // week while "today" stays at the reference date, so paging away really does dim the pills and
    // light up "This week".
    val state =
        WeekUiState.Content(
            weekStart = weekStart,
            habits = SampleHabits.weekPeriods(reference, completed, weekStart),
            isCurrentWeek = weekStart == thisWeek,
        )

    WeekContent(
        state = state,
        onEvent = { event ->
            when (event) {
                is WeekEvent.DayToggled -> {
                    val row = Completion(event.habitId, event.date)
                    completed = if (event.completed) completed + row else completed - row
                }

                WeekEvent.PreviousWeekPressed -> {
                    weekStart = weekStart.minusDays(DAYS_PER_WEEK.toLong())
                }

                WeekEvent.NextWeekPressed -> {
                    weekStart = weekStart.plusDays(DAYS_PER_WEEK.toLong())
                }

                WeekEvent.ThisWeekPressed -> {
                    weekStart = thisWeek
                }
            }
        },
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}
