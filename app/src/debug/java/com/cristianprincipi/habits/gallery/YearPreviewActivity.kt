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
import androidx.compose.ui.Modifier
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.HabitsEmptyReason
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.feature.history.YearContent
import com.cristianprincipi.habits.feature.history.YearUiState

/**
 * The Year screen against `SampleHabits`, with no Room and no Notion behind it (M8.3).
 *
 * The same reason and the same safety as [WeekPreviewActivity] and [MonthPreviewActivity]: the
 * screen shows nothing until a Notion connection has imported habits, and connecting costs the
 * **user** a paste of a token only they have. Nothing here touches a repository.
 *
 * ⭐ **The one thing worth looking at here is the horizontal scroll.** `YearDotGrid` is about 316dp
 * wide at `yearDotSmall` and scrolls itself to the end — today — on first composition, which is the
 * opposite of what the web prototype does. Whether that lands on a real screen, and whether five of
 * them inside a `LazyColumn` still feel right, is not something a unit test can answer.
 *
 * ```
 * adb shell am start -n com.cristianprincipi.habits/com.cristianprincipi.habits.gallery.YearPreviewActivity
 * adb shell am start -S -n …/….gallery.YearPreviewActivity --es state pending
 * adb shell am start -S -n …/….gallery.YearPreviewActivity --es state failed
 * ```
 *
 * ⚠ `-S` matters when the activity is already up: `am start` compares intents with
 * `filterEquals`, which **ignores extras**, so without it a second launch resumes the old instance
 * and the state extra is silently dropped.
 */
class YearPreviewActivity : ComponentActivity() {
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
                    SampleYear(empty = emptyReasonFrom(intent.getStringExtra(EXTRA_STATE)))
                }
            }
        }
    }
}

private const val EXTRA_STATE = "state"

/** `null` for the sample year; a reason to render [YearUiState.Empty] instead. */
private fun emptyReasonFrom(extra: String?): HabitsEmptyReason? =
    when (extra) {
        "pending" -> HabitsEmptyReason.SyncPending
        "failed" -> HabitsEmptyReason.SyncFailed
        else -> null
    }

/**
 * The real [YearContent] over `SampleHabits`.
 *
 * No local state at all, because the screen has none: nothing on it can be tapped, and the window is
 * always the trailing 365 days. It takes its own `safeDrawing` insets because there is no shell
 * around it here (ADR-063's rule applies inside the graph; this is outside it).
 */
@Composable
private fun SampleYear(empty: HabitsEmptyReason?) {
    YearContent(
        state = empty?.let(YearUiState::Empty) ?: YearUiState.Content(SampleHabits.years()),
        // Real navigation is the shell's; here it is the one thing this activity cannot show.
        onOpenSyncStatus = {},
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}
