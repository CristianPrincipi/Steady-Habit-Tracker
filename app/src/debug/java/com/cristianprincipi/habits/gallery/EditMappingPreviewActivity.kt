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
import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.SyncFailure
import com.cristianprincipi.habits.feature.notionsetup.EditMappingContent
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupEvent
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupStep
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupUiState

/**
 * The edit-mapping screen against a sample schema, with no Notion behind it.
 *
 * ### Why this exists — [ManageHabitsPreviewActivity]'s reason, one screen along
 *
 * `EditMappingScreen` reads the **live** schema on open, so without a connection it can only ever
 * show its "could not read the schema" state — and connecting costs the **user** a paste of a token
 * only they have (`CLAUDE.md` §7). This screen is in no Figma frame, and it carries the one piece of
 * copy the user designed from prose rather than from pixels: the note naming the habits a change
 * would stop syncing. That note has to be *looked at*.
 *
 * The ticking is live, so the note appearing and withdrawing can be watched rather than imagined.
 *
 * ```
 * adb shell am start -n …/….gallery.EditMappingPreviewActivity                  # three habits mapped
 * adb shell am start -n …/….gallery.EditMappingPreviewActivity --es state dropping
 * adb shell am start -n …/….gallery.EditMappingPreviewActivity --es state added
 * adb shell am start -n …/….gallery.EditMappingPreviewActivity --es state offline
 * ```
 *
 * ### Exported, and safe to be
 *
 * Sample data in memory and no repository: nothing here can reach a workspace, save a mapping or
 * touch the stored token. Debug source set only, so it cannot reach a release build.
 */
class EditMappingPreviewActivity : ComponentActivity() {
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
                    // ⚠ The harness supplies the insets, because there is no shell here to do it —
                    // the mistake M7.5's first screenshot caught, one screen along.
                    SampleMapping(
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
private fun SampleMapping(
    variant: String?,
    modifier: Modifier = Modifier,
) {
    var state by remember { mutableStateOf(stateFor(variant)) }

    EditMappingContent(
        state = state,
        onEvent = { event ->
            state =
                when (event) {
                    is NotionSetupEvent.HabitToggled -> {
                        val ticked = state.habitPropertyIds
                        state.copy(
                            habitPropertyIds =
                                if (event.propertyId in ticked) {
                                    ticked - event.propertyId
                                } else {
                                    ticked + event.propertyId
                                },
                        )
                    }

                    is NotionSetupEvent.DayKeySelected -> {
                        state.copy(dayKeyPropertyId = event.propertyId)
                    }

                    // The retry, which here just loads the sample schema.
                    NotionSetupEvent.DatabaseConfirmed -> {
                        stateFor(null)
                    }

                    else -> {
                        state
                    }
                }
        },
        modifier = modifier,
    )
}

/** The user's real shape, near enough: a date column, three habits, and two columns that are neither. */
private val sampleProperties =
    listOf(
        NotionProperty("d", "Date", "date", NotionPropertyRole.DayKey),
        NotionProperty("r", "📖 Read", "checkbox", NotionPropertyRole.Habit),
        NotionProperty("j", "✒️ Journal", "checkbox", NotionPropertyRole.Habit),
        NotionProperty("t", "🥕 Diet", "checkbox", NotionPropertyRole.Habit),
        NotionProperty("w", "Weight", "number", NotionPropertyRole.Unavailable),
        NotionProperty("p", "Progress", "formula", NotionPropertyRole.Unavailable),
    )

private fun stateFor(variant: String?): NotionSetupUiState =
    when (variant) {
        // ⭐ The state the screen exists to be careful about: Journal is mapped and has just been
        // unticked, so the note under the habit rows names it.
        "dropping" -> {
            mapped(ticked = setOf("r", "t"))
        }

        // A column added in Notion since setup, arriving unticked (ADR-051) — the case that made
        // this whole step necessary.
        "added" -> {
            mapped(ticked = setOf("r", "j"), previously = setOf("r", "j"))
        }

        "offline" -> {
            NotionSetupUiState(step = NotionSetupStep.MapProperties, failure = SyncFailure.Offline)
        }

        else -> {
            mapped(ticked = setOf("r", "j", "t"))
        }
    }

private fun mapped(
    ticked: Set<String>,
    previously: Set<String> = setOf("r", "j", "t"),
) = NotionSetupUiState(
    step = NotionSetupStep.MapProperties,
    properties = sampleProperties,
    dayKeyPropertyId = "d",
    habitPropertyIds = ticked,
    previouslyMappedHabitIds = previously,
)
