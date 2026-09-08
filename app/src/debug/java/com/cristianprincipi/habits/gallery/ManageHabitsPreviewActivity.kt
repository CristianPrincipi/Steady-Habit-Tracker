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
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.usecase.moved
import com.cristianprincipi.habits.feature.managehabits.ManageHabitsContent
import com.cristianprincipi.habits.feature.managehabits.ManageHabitsEvent
import com.cristianprincipi.habits.feature.managehabits.ManageHabitsUiState

/**
 * The habit list against `SampleHabits`, with no Room and no Notion behind it.
 *
 * ### Why this exists — `WeekPreviewActivity`'s reason, one screen along
 *
 * `ManageHabitsScreen` shows nothing until a Notion connection has imported some habits, and
 * connecting costs the **user** a paste of a token only they have (`CLAUDE.md` §7). This screen is
 * in no Figma frame at all, so it is exactly the kind of thing that has to be *looked at* before it
 * is shown to anyone — and it wires the real [ManageHabitsContent], not a debug re-creation of it.
 *
 * The colour picking is live: the tap edits a local `mutableStateOf`, so the swatch behaviour and
 * the icon tint following it can both be watched here.
 *
 * ```
 * adb shell am start -n …/….gallery.ManageHabitsPreviewActivity                 # five habits, link known
 * adb shell am start -n …/….gallery.ManageHabitsPreviewActivity --es state nolink
 * adb shell am start -n …/….gallery.ManageHabitsPreviewActivity --es state empty
 * ```
 *
 * ### Exported, and safe to be
 *
 * Sample data in memory and no repository: nothing here can touch a live workspace, unlike
 * [ShellHarnessActivity], which hosts the real sync-status screen and its disconnect. Debug source
 * set only, so it cannot reach a release build.
 */
class ManageHabitsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // ADR-019: matching MainActivity rather than taking the default, which follows the system
        // light/dark setting and renders dark-on-dark system bars.
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
                    // ⚠ **The harness supplies the insets, because there is no shell here to do
                    // it** — `StubScreen`'s rule from the other end. In the app this screen sits
                    // inside `HabitsShell`'s `Scaffold` and takes the padding it is handed, so it
                    // adds none of its own; standing alone, it needs someone to. Without this the
                    // title renders over the status bar, which is exactly what the first screenshot
                    // of this activity showed.
                    SampleHabitList(
                        variant = intent.getStringExtra(EXTRA_STATE),
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                    )
                }
            }
        }
    }
}

private const val EXTRA_STATE = "state"

@Composable
private fun SampleHabitList(
    variant: String?,
    modifier: Modifier = Modifier,
) {
    var habits by remember { mutableStateOf(habitsFor(variant)) }

    ManageHabitsContent(
        state =
            ManageHabitsUiState.Content(
                habits = habits,
                // The disabled-button case is the one worth being able to ask for: it is what every
                // connection looks like until its first sync lands, and no screenshot would ever
                // catch it by accident.
                notionUrl = if (variant == "nolink") null else SAMPLE_URL,
            ),
        onEvent = { event ->
            when (event) {
                is ManageHabitsEvent.ColorPicked -> {
                    habits =
                        habits.map { habit ->
                            if (habit.id == event.habitId) habit.copy(color = event.color) else habit
                        }
                }

                // M15.5. The harness reuses the domain's own `moved`, so what a screenshot shows is
                // the rule the app runs rather than a second implementation that could disagree
                // with it - including the no-op at either end.
                is ManageHabitsEvent.Moved -> {
                    habits = habits.moved(event.habitId, event.direction) ?: habits
                }
            }
        },
        onOpenInNotion = { },
        onEditMapping = { },
        modifier = modifier,
    )
}

private fun habitsFor(variant: String?): List<Habit> =
    when (variant) {
        "empty" -> {
            emptyList()
        }

        else -> {
            SampleHabits.habits()
        }
    }

/** A link of the right shape and no real workspace in it — `NotionDatabaseLinkTest`'s rule. */
private const val SAMPLE_URL = "https://www.notion.so/8f2c41d97b5e40a3ac6e1d9f0b73e254"
