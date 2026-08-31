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
import com.cristianprincipi.habits.feature.history.MonthContent
import com.cristianprincipi.habits.feature.history.MonthEvent
import com.cristianprincipi.habits.feature.history.MonthUiState
import java.time.YearMonth

/**
 * The Month screen against `SampleHabits`, with no Room and no Notion behind it (M8.1).
 *
 * Exactly [WeekPreviewActivity]'s reason and exactly its safety: this screen shows nothing until a
 * Notion connection has imported habits, and connecting costs the **user** a paste of a token only
 * they have — so without this, the milestone that builds the screen could not look at it. Nothing
 * here touches a repository; paging edits a local `mutableStateOf`.
 *
 * Paging is real, so the two things worth watching are both reachable: a month steps and the grid
 * reshapes under it — February is four columns shorter than August and starts on a different
 * weekday — and the leading blanks move with it.
 *
 * ```
 * adb shell am start -n com.cristianprincipi.habits/com.cristianprincipi.habits.gallery.MonthPreviewActivity
 * adb shell am start -n …/….gallery.MonthPreviewActivity --es state pending
 * adb shell am start -n …/….gallery.MonthPreviewActivity --es state failed
 * ```
 */
class MonthPreviewActivity : ComponentActivity() {
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
                    SampleMonth(empty = emptyReasonFrom(intent.getStringExtra(EXTRA_STATE)))
                }
            }
        }
    }
}

private const val EXTRA_STATE = "state"

/** `null` for the sample month; a reason to render [MonthUiState.Empty] instead. */
private fun emptyReasonFrom(extra: String?): HabitsEmptyReason? =
    when (extra) {
        "pending" -> HabitsEmptyReason.SyncPending
        "failed" -> HabitsEmptyReason.SyncFailed
        else -> null
    }

/**
 * The real [MonthContent] over an in-memory month the arrows actually move.
 *
 * "Today" stays at `SampleHabits.referenceDate` while the shown month moves, so paging back really
 * does fill the grid and paging forward really does empty it into `FUTURE` cells — which is the one
 * thing a static preview cannot show. It takes its own `safeDrawing` insets because there is no
 * shell around it here (ADR-063's rule applies inside the graph; this is outside it).
 */
@Composable
private fun SampleMonth(empty: HabitsEmptyReason?) {
    val reference = SampleHabits.referenceDate
    var month by remember { mutableStateOf(YearMonth.from(reference)) }

    val state =
        empty?.let { reason -> MonthUiState.Empty(month = month, reason = reason) }
            ?: MonthUiState.Content(
                month = month,
                habits = SampleHabits.monthPeriods(reference, monthOf = month),
                isCurrentMonth = month == YearMonth.from(reference),
            )

    MonthContent(
        state = state,
        onEvent = { event ->
            month =
                when (event) {
                    MonthEvent.PreviousMonthPressed -> month.minusMonths(1)
                    MonthEvent.NextMonthPressed -> month.plusMonths(1)
                    MonthEvent.ThisMonthPressed -> YearMonth.from(reference)
                }
        },
        // Real navigation is the shell's; here it is the one thing this activity cannot show.
        onOpenSyncStatus = {},
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}
