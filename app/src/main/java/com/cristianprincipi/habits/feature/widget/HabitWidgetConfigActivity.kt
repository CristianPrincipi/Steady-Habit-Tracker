package com.cristianprincipi.habits.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.appwidget.updateAll
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.HabitIcon
import com.cristianprincipi.habits.core.designsystem.component.HabitIconSize
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing
import com.cristianprincipi.habits.core.ui.resolve
import com.cristianprincipi.habits.domain.model.Habit
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.HabitId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Asks which habit a newly placed widget should show.
 *
 * ⚠⚠ **A configuration activity has a contract, and getting it wrong strands widgets.** The system
 * treats anything other than `RESULT_OK` as "the user backed out" and removes the widget — so the
 * result is set to cancelled **first**, before any UI exists, and only replaced on a successful
 * pick. An activity that forgot this would leave a widget on the home screen that had never been
 * told what to draw.
 */
@AndroidEntryPoint
class HabitWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark bars for the same reason MainActivity forces them: v1 is dark-only regardless of the
        // system setting, and the default follows that setting (ADR-009).
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val appWidgetId =
            intent?.extras?.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID,
            ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            // Launched outside the widget flow. Nothing to configure, and no result to report.
            finish()
            return
        }

        setContent {
            HabitsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    HabitWidgetConfigScreen(
                        onPicked = { habit -> bind(appWidgetId, habit) },
                        // ⚠⚠ Without this the title draws UNDER the status bar and collides with
                        // the clock — seen on an emulator screenshot, not reasoned about.
                        // MainActivity needs no equivalent because HabitsShell's Scaffold consumes
                        // the insets for it; this activity has no shell, so edge-to-edge here means
                        // edge-to-edge all the way into the system bars (CLAUDE.md §6).
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
        }
    }

    /**
     * Stores the choice against this widget, draws it once, and reports success.
     *
     * ⭐ **The explicit `updateAll` is what makes the widget appear filled rather than empty.**
     * Writing the state alone changes nothing on screen — Glance re-reads only when something asks
     * it to, and at this moment nothing has: the widget was composed before the user picked.
     */
    private fun bind(
        appWidgetId: Int,
        habit: Habit,
    ) {
        lifecycleScope.launch {
            // ⚠⚠ getGlanceIdBy THROWS for an id the system no longer knows, and this crashed on a
            // real device. The id is handed to us by the launcher and is valid when the screen
            // opens — but the widget can be removed while the screen is still up, and then a tap
            // takes the whole app down instead of quietly backing out. The result is already
            // RESULT_CANCELED, which is exactly the right answer for a widget that no longer
            // exists, so there is nothing to report and nothing to say.
            val glanceId =
                runCatching {
                    GlanceAppWidgetManager(this@HabitWidgetConfigActivity).getGlanceIdBy(appWidgetId)
                }.getOrNull()
            if (glanceId == null) {
                finish()
                return@launch
            }
            updateAppWidgetState(
                context = this@HabitWidgetConfigActivity,
                definition = PreferencesGlanceStateDefinition,
                glanceId = glanceId,
            ) { preferences ->
                preferences.toMutablePreferences().apply {
                    this[HabitGridWidget.HABIT_ID] = habit.id.value
                }
            }
            HabitGridWidget().updateAll(this@HabitWidgetConfigActivity)
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}

@Composable
private fun HabitWidgetConfigScreen(
    onPicked: (Habit) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HabitWidgetConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HabitWidgetConfigContent(state = state, onPicked = onPicked, modifier = modifier)
}

@Composable
private fun HabitWidgetConfigContent(
    state: HabitWidgetConfigUiState,
    onPicked: (Habit) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(Spacing.screenPadding)) {
        Text(
            text = stringResource(R.string.widget_config_title),
            style = MaterialTheme.typography.headlineMedium,
            color = HabitsTheme.colors.text.primary,
        )
        when {
            // Nothing yet is not the same as nothing at all — see HabitWidgetConfigUiState.
            !state.loaded -> {
                Unit
            }

            state.habits.isEmpty() -> {
                Text(
                    modifier = Modifier.padding(top = Spacing.cardGap),
                    text = stringResource(R.string.widget_config_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HabitsTheme.colors.text.secondary,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.padding(top = Spacing.cardGap),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                ) {
                    items(items = state.habits, key = { habit -> habit.id.value }) { habit ->
                        HabitPickerRow(habit = habit, onPicked = onPicked)
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitPickerRow(
    habit: Habit,
    onPicked: (Habit) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onPicked(habit) }
                .padding(vertical = Spacing.chipGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
    ) {
        HabitIcon(
            emoji = habit.emoji,
            name = habit.name,
            habitColor = habit.color.resolve(),
            size = HabitIconSize.SMALL,
        )
        Text(
            text = habit.name,
            style = MaterialTheme.typography.titleMedium,
            color = HabitsTheme.colors.text.primary,
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun HabitWidgetConfigPreview() {
    HabitsTheme {
        HabitWidgetConfigContent(
            state =
                HabitWidgetConfigUiState(
                    habits =
                        listOf(
                            previewHabit("Read", "📖", HabitColor.BLUE),
                            previewHabit("Journal", "✒️", HabitColor.GREEN),
                            previewHabit("Quote", "📜", HabitColor.PURPLE),
                        ),
                    loaded = true,
                ),
            onPicked = {},
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun HabitWidgetConfigEmptyPreview() {
    HabitsTheme {
        HabitWidgetConfigContent(state = HabitWidgetConfigUiState(loaded = true), onPicked = {})
    }
}

private fun previewHabit(
    name: String,
    emoji: String,
    color: HabitColor,
): Habit =
    Habit(
        id = HabitId(name),
        name = name,
        emoji = emoji,
        color = color,
        startDate = PREVIEW_START_DATE,
    )

/** Parsed rather than `LocalDate.of(2026, 1, 1)`, whose month and day read as magic numbers. */
private val PREVIEW_START_DATE: LocalDate = LocalDate.parse("2026-01-01")
