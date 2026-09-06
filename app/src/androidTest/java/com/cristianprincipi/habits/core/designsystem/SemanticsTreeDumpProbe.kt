package com.cristianprincipi.habits.core.designsystem

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.core.datetime.Elapsed
import com.cristianprincipi.habits.core.datetime.startOfWeek
import com.cristianprincipi.habits.core.designsystem.component.ConnectionSummary
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.ui.preview.SampleHabits
import com.cristianprincipi.habits.domain.model.NotionProperty
import com.cristianprincipi.habits.domain.model.NotionPropertyRole
import com.cristianprincipi.habits.domain.model.NotionSource
import com.cristianprincipi.habits.feature.about.AboutContent
import com.cristianprincipi.habits.feature.history.MonthContent
import com.cristianprincipi.habits.feature.history.MonthUiState
import com.cristianprincipi.habits.feature.history.YearContent
import com.cristianprincipi.habits.feature.history.YearUiState
import com.cristianprincipi.habits.feature.managehabits.ManageHabitsContent
import com.cristianprincipi.habits.feature.managehabits.ManageHabitsUiState
import com.cristianprincipi.habits.feature.notionsetup.EditMappingContent
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupContent
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupStep
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupUiState
import com.cristianprincipi.habits.feature.notionsetup.ReplaceTokenContent
import com.cristianprincipi.habits.feature.settings.SettingsContent
import com.cristianprincipi.habits.feature.settings.SettingsUiState
import com.cristianprincipi.habits.feature.stats.StatsContent
import com.cristianprincipi.habits.feature.stats.StatsUiState
import com.cristianprincipi.habits.feature.syncstatus.SyncStatusContent
import com.cristianprincipi.habits.feature.syncstatus.SyncStatusUiState
import com.cristianprincipi.habits.feature.week.WeekContent
import com.cristianprincipi.habits.feature.week.WeekUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.YearMonth

/**
 * M13.2 — **a transcript of what TalkBack would say, screen by screen.**
 *
 * Temporary. This is a diagnostic, not an assertion: it walks the *merged* semantics tree — the one
 * the accessibility framework serves — and writes one line per node the reader would stop on, in
 * tree order. The output is read by a human, which is the whole point: reading order and grouping
 * are judgements, and a tree dump is the only way to make them against the running app rather than
 * against the source.
 */
@RunWith(AndroidJUnit4::class)
class SemanticsTreeDumpProbe {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Every node a screen reader stops on, in tree order, with what it would announce. */
    private fun transcribe(node: SemanticsNode): List<String> {
        val spoken = node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(", ")
        val text = node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" | ")
        val edited = node.config.getOrNull(SemanticsProperties.EditableText)?.text
        val role = node.config.getOrNull(SemanticsProperties.Role)?.toString()
        val actions = node.config.filter { it.key.name.startsWith("On") }.map { it.key.name }
        val toggle = node.config.getOrNull(SemanticsProperties.ToggleableState)?.toString()
        val selected = node.config.getOrNull(SemanticsProperties.Selected)?.toString()
        val disabled = node.config.getOrNull(SemanticsProperties.Disabled) != null

        val says =
            listOfNotNull(
                spoken?.let { "desc=\"$it\"" },
                text?.let { "text=\"$it\"" },
                edited?.let { "editable=\"$it\"" },
                role?.let { "role=$it" },
                toggle?.let { "toggle=$it" },
                selected?.let { "selected=$it" },
                if (disabled) "DISABLED" else null,
                if (actions.isEmpty()) null else actions.joinToString(",", prefix = "[", postfix = "]"),
            )

        val self = if (says.isEmpty()) emptyList() else listOf(says.joinToString("  "))
        return self + node.children.flatMap(::transcribe)
    }

    private val month = YearMonth.from(SampleHabits.referenceDate)

    /** Screens with nothing to tap are here too: reading order is not only about controls. */
    private val screens: List<Pair<String, @Composable () -> Unit>> =
        listOf(
            "Week" to {
                WeekContent(
                    state =
                        WeekUiState.Content(
                            weekStart = startOfWeek(SampleHabits.referenceDate),
                            habits = SampleHabits.weekPeriods(SampleHabits.referenceDate),
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
            "Year" to {
                YearContent(
                    state = YearUiState.Content(SampleHabits.years(SampleHabits.referenceDate)),
                    onOpenSyncStatus = {},
                )
            },
            "Stats" to {
                StatsContent(
                    state =
                        StatsUiState.Content(
                            habits = SampleHabits.habits(),
                            selected = SampleHabits.stats(id = SampleHabits.habits().first().id.value),
                        ),
                    onEvent = {},
                    onOpenSyncStatus = {},
                )
            },
            "Settings" to {
                SettingsContent(
                    state = SettingsUiState(connection = ConnectionSummary(isConnected = true, lastSynced = Elapsed.Minutes(3L))),
                    onOpenSyncStatus = {},
                    onOpenHabits = {},
                    onOpenAbout = {},
                )
            },
            "Sync status" to {
                SyncStatusContent(
                    state = SyncStatusUiState(isConnected = true, lastSynced = Elapsed.Minutes(3L)),
                    onEvent = {},
                    onEditMapping = {},
                    onReplaceToken = {},
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
            "About" to { AboutContent(versionName = "0.1.0", versionCode = 1) },
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
                    state = NotionSetupUiState(step = NotionSetupStep.MapProperties, properties = dumpProperties),
                    onEvent = {},
                )
            },
            "Edit mapping" to {
                EditMappingContent(
                    state = NotionSetupUiState(step = NotionSetupStep.MapProperties, properties = dumpProperties),
                    onEvent = {},
                )
            },
            "Replace token" to {
                ReplaceTokenContent(state = NotionSetupUiState(token = "ntn_x"), onEvent = {})
            },
        )

    @Test
    fun dumpEveryScreensReadingOrder() {
        var showing by mutableIntStateOf(0)
        composeTestRule.setContent { HabitsTheme { screens[showing].second() } }

        val report = StringBuilder()
        screens.indices.forEach { index ->
            composeTestRule.runOnIdle { showing = index }
            composeTestRule.waitForIdle()
            val root = composeTestRule.onRoot().fetchSemanticsNode()
            report.appendLine("═══ ${screens[index].first} ═══")
            transcribe(root).forEachIndexed { i, line -> report.appendLine("  ${i + 1}. $line") }
            report.appendLine()
        }

        // ⚠ Deliberately red. `connectedAndroidTest` uninstalls the app when it finishes, taking the
        // external files dir with it, and instrumentation stdout is not captured in the XML report.
        // A failure message is: it is the one channel that survives to the host.
        throw AssertionError(report.toString())
    }

    private companion object {
        /** One of each role, so the mapping step draws a radio row, a checkbox row and a dead one. */
        val dumpProperties =
            listOf(
                NotionProperty("d", "Date", "date", NotionPropertyRole.DayKey),
                NotionProperty("r", "Read", "checkbox", NotionPropertyRole.Habit),
                NotionProperty("j", "Journal", "checkbox", NotionPropertyRole.Habit),
                NotionProperty("n", "Notes", "rich_text", NotionPropertyRole.Unavailable),
            )
    }
}
