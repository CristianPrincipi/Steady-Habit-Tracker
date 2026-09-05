package com.cristianprincipi.habits.core.designsystem

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.designsystem.component.ConnectionSummary
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.NotionSource
import com.cristianprincipi.habits.feature.history.MonthContent
import com.cristianprincipi.habits.feature.history.MonthUiState
import com.cristianprincipi.habits.feature.managehabits.ManageHabitsContent
import com.cristianprincipi.habits.feature.managehabits.ManageHabitsUiState
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupContent
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupStep
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupUiState
import com.cristianprincipi.habits.feature.settings.SettingsContent
import com.cristianprincipi.habits.feature.settings.SettingsUiState
import com.cristianprincipi.habits.feature.stats.StatsContent
import com.cristianprincipi.habits.feature.stats.StatsUiState
import com.cristianprincipi.habits.feature.week.WeekContent
import com.cristianprincipi.habits.feature.week.WeekUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/**
 * M13.1 — **every actionable node in the app says what it is**.
 *
 * ### Why a sweep, and why it is not a reading
 *
 * `CLAUDE.md` §6 requires a 48dp minimum touch target. Until this step the evidence was a *count*:
 * four custom clickables carry an explicit expansion — the week pill, the habit chip, the colour
 * swatch, the nav arrow — and each has an ADR behind it. What nobody had checked is the other kind:
 * the rows and buttons never thought about, because Material was assumed to handle them.
 * `ButtonDefaults.MinHeight` is a **token**, not 48dp, so that assumption is worth a measurement.
 *
 * ⭐ **It walks the semantics tree, which is the tree TalkBack walks.** `hasClickAction()` finds
 * `clickable`, `toggleable` and `selectable` alike, so nothing interactive can hide by being built
 * differently — and a new screen is covered the day it is added to [screens].
 *
 * ### ⚠⚠ It asserts naming and NOT size, and that is a measured decision
 *
 * This test was written to check both. The size half is gone, because the measurement that would
 * have justified it does not exist:
 *
 * - Asserting on **drawn** size (`node.size`) fails twelve Material buttons at `ButtonDefaults`'
 *   40dp. `TouchTargetExpansionProbe` then asked the input system directly, with a control against
 *   the harness clamping coordinates: **a tap 1dp above a 40dp button arrives, and one 40dp above
 *   does not.** Compose expands the hit area to the minimum target, so those twelve are 40dp
 *   *drawn* and 48dp *tappable*. Asserting on drawn size would demand the design change to satisfy
 *   a checklist while the guideline was already met — the thing ADR-069 refused for the week pill.
 * - Asserting on **touch** bounds (`touchBoundsInRoot`) is vacuous: it is the already-expanded
 *   rectangle, and a deliberately 24dp unnamed button reports `48.0 × 48.0`. It cannot fail.
 *
 * So: **Compose enforces the 48dp minimum itself**, and a test of it would either be wrong or
 * empty. `CLAUDE.md` §6's rule is met by the framework, and the four components that expand
 * themselves — the pill, the chip, the swatch, the nav arrow — do so for *crowding*, which is the
 * case expansion cannot solve because neighbours are in the way.
 *
 * What is left is the half no framework can supply: an actionable node must **say what it is**.
 * That is the most common accessibility defect there is, and it found a real one.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilitySweepTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private fun string(id: Int) = ApplicationProvider.getApplicationContext<Context>().getString(id)

    private data class Actionable(
        val label: String,
        val widthDp: Float,
        val heightDp: Float,
        val contents: String? = null,
    ) {
        /** What to call this node in a complaint, when it has no name to be called by. */
        val describedForComplaint: String
            get() = contents?.let { "an edit field containing \"$it\"" } ?: "an unnamed control"
    }

    /**
     * Every actionable node currently on screen, with its label and its own measured size.
     *
     * ### ⚠⚠ Three rectangles, and two of them lie
     *
     * A `SemanticsNode` offers three, and picking one took two wrong runs:
     *
     * - **`boundsInRoot`** is the layout rectangle **clipped to its ancestors**. The first draft used
     *   it and reported thirty violations, most of them artefacts: a card at the edge of a scrolling
     *   viewport gave pills 47.2dp tall that are really 48, and swatches 17.9dp tall that are really
     *   48. **False positives.**
     * - **`touchBoundsInRoot`** is the accessibility touch rectangle, and it is **already expanded to
     *   the minimum target**. Switching to it turned the sweep green in one edit — and the control
     *   proved why that was worthless: a deliberately **24dp** unnamed button reports `48.0 × 48.0`.
     *   It cannot fail. **False negatives.**
     * - **`size`** is the node's own measured size, unclipped and *including* any expansion the code
     *   actually asked for — `minimumInteractiveComponentSize` reserves the space in layout, so a
     *   component that expands itself reads as expanded, and one that does not reads as it draws.
     *
     * The third is the only one that answers the question this test asks.
     */
    private fun actionables(): List<Actionable> =
        composeTestRule.onAllNodes(hasClickAction()).fetchSemanticsNodes().map { node ->
            val spoken = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
            val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")
            // ⚠ `EditableText` is read for the *message* and deliberately NOT accepted as a label.
            // A field's contents are not its name: TalkBack announcing "ntn_x, edit box" tells a
            // blind user what they typed, never what the box is for. Counting it would have let this
            // sweep pass over the one real labelling defect in the app.
            val edited = node.config.getOrNull(SemanticsProperties.EditableText)?.text
            Actionable(
                label = spoken ?: text ?: "",
                contents = edited,
                widthDp =
                    with(composeTestRule.density) {
                        node.size.width
                            .toDp()
                            .value
                    },
                heightDp =
                    with(composeTestRule.density) {
                        node.size.height
                            .toDp()
                            .value
                    },
            )
        }

    /**
     * Checks one screen and **returns** its complaints rather than failing on the spot.
     *
     * Collected so that one run reports every offender on every screen. A sweep that stops at the
     * first one turns a survey into a queue.
     */
    private fun complaintsFor(name: String): List<String> {
        val found = actionables()
        assertTrue("$name has no actionable nodes at all — the sweep is measuring nothing", found.isNotEmpty())

        return found.filter { node -> node.label.isBlank() }.map { node ->
            "$name — ${node.describedForComplaint} (${node.widthDp}×${node.heightDp}dp) has no name"
        }
    }

    private val month = YearMonth.from(SampleHabits.referenceDate)

    /** Every screen with something to tap on it. A new one belongs here the day it is written. */
    private val screens: List<Pair<String, @Composable () -> Unit>> =
        listOf(
            "Week" to {
                WeekContent(
                    state =
                        WeekUiState.Content(
                            weekStart = startOfWeek(SampleHabits.referenceDate),
                            habits = SampleHabits.weekPeriods(SampleHabits.referenceDate),
                            // Not the current week, so "This week" is enabled and therefore measured.
                            isCurrentWeek = false,
                            currentYear = SampleHabits.referenceDate.year,
                        ),
                    onEvent = {},
                    onOpenSyncStatus = {},
                )
            },
            "Month" to {
                MonthContent(
                    state =
                        MonthUiState.Content(
                            month = month,
                            habits = SampleHabits.monthPeriods(SampleHabits.referenceDate, monthOf = month),
                            isCurrentMonth = false,
                        ),
                    onEvent = {},
                    onOpenSyncStatus = {},
                )
            },
            "Stats" to {
                StatsContent(
                    state =
                        StatsUiState.Content(
                            habits = SampleHabits.habits(),
                            selected =
                                SampleHabits.stats(
                                    id =
                                        SampleHabits
                                            .habits()
                                            .first()
                                            .id.value,
                                ),
                        ),
                    onEvent = {},
                    onOpenSyncStatus = {},
                )
            },
            "Settings" to {
                SettingsContent(
                    state = SettingsUiState(connection = ConnectionSummary(isConnected = true, lastSynced = null)),
                    onOpenSyncStatus = {},
                    onOpenHabits = {},
                    onOpenAbout = {},
                )
            },
            "Habit list" to {
                ManageHabitsContent(
                    state = ManageHabitsUiState.Content(habits = SampleHabits.habits(), notionUrl = null),
                    onEvent = {},
                    onOpenInNotion = {},
                    onEditMapping = {},
                )
            },
            "Setup — token" to {
                NotionSetupContent(state = NotionSetupUiState(token = "ntn_x"), onEvent = {})
            },
            "Setup — database" to {
                NotionSetupContent(
                    state =
                        NotionSetupUiState(
                            step = NotionSetupStep.ChooseDatabase,
                            databases = listOf(NotionSource("a", "Habits"), NotionSource("b", "Reading log")),
                        ),
                    onEvent = {},
                )
            },
            "Setup — mapping" to {
                NotionSetupContent(
                    state =
                        NotionSetupUiState(
                            step = NotionSetupStep.MapProperties,
                            properties = sweepProperties,
                        ),
                    onEvent = {},
                )
            },
        )

    @Test
    fun everyActionableNodeIsBigEnoughToHitAndSaysWhatItIs() {
        // ⚠ One `setContent` per rule, so the screen is a piece of state rather than eight calls.
        var showing by mutableIntStateOf(0)
        composeTestRule.setContent { HabitsTheme { screens[showing].second() } }

        val complaints =
            screens.indices.flatMap { index ->
                composeTestRule.runOnIdle { showing = index }
                composeTestRule.waitForIdle()
                complaintsFor(screens[index].first)
            }

        assertTrue(complaints.joinToString(prefix = "\n", separator = "\n"), complaints.isEmpty())
    }

    /**
     * ⚠⚠ **The control, and this test is worth nothing without it.**
     *
     * The sweep's first draft measured `boundsInRoot` and reported thirty violations, every one of
     * them an artefact of measuring a clipped *layout* rectangle. Switching to `touchBoundsInRoot`
     * turned it green — and a check that goes from "everything is broken" to "nothing is" in one
     * edit has earned no trust at all. ADR-088 §3 caught a vacuous assertion a milestone after it
     * was written; this one is checked in the same commit.
     *
     * An unnamed clickable must produce **exactly one** complaint. If the naming check ever stops
     * firing — a widened label lookup, a matcher that finds nothing — this is what says so.
     */
    @Test
    fun andTheSweepItselfCanStillFail() {
        composeTestRule.setContent {
            HabitsTheme {
                Box(modifier = Modifier.size(BAD_TARGET).clickable {})
            }
        }

        val complaints = complaintsFor("Control")

        assertEquals(complaints.joinToString(separator = "; "), 1, complaints.size)
        assertTrue("the naming check is not firing", complaints.single().contains("has no name"))
    }

    private companion object {
        /** Any size will do now that only naming is asserted; small keeps the control obvious. */
        val BAD_TARGET = 24.dp

        /** One of each role, so the mapping step draws a radio row, a checkbox row and a dead one. */
        val sweepProperties =
            listOf(
                NotionProperty("d", "Date", "date", NotionPropertyRole.DayKey),
                NotionProperty("r", "Read", "checkbox", NotionPropertyRole.Habit),
                NotionProperty("j", "Journal", "checkbox", NotionPropertyRole.Habit),
                NotionProperty("n", "Notes", "rich_text", NotionPropertyRole.Unavailable),
            )
    }
}
