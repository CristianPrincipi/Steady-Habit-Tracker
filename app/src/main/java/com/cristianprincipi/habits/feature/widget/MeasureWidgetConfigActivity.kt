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
import com.cristianprincipi.habits.domain.model.HabitColor
import com.cristianprincipi.habits.domain.model.Measure
import com.cristianprincipi.habits.domain.model.MeasureId
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

/**
 * Asks which number a newly placed measure widget should show — [HabitWidgetConfigActivity]'s twin.
 *
 * ⚠⚠ **A configuration activity has a contract, and getting it wrong strands widgets.** Anything
 * other than `RESULT_OK` means "the user backed out" and the system removes the widget — so the
 * result is set to cancelled **before any UI exists** and replaced only on a successful pick.
 */
@AndroidEntryPoint
class MeasureWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Dark bars because v1 is dark-only regardless of the system setting (ADR-009).
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
                    MeasureWidgetConfigScreen(
                        onPicked = { measure -> bind(appWidgetId, measure) },
                        // Without this the title draws UNDER the status bar: this activity has no
                        // shell to consume the insets for it (CLAUDE.md §6).
                        modifier = Modifier.safeDrawingPadding(),
                    )
                }
            }
        }
    }

    /**
     * Stores the choice against this widget, draws it once, and reports success.
     *
     * ⚠⚠ **`update(glanceId)` and NOT `updateAll`**, measured on a real device at M15.6b: each
     * Glance composition runs in its own WorkManager job that lives for a 45-second idle timeout, so
     * `updateAll` filled the default four-thread pool and left the next widget queued behind it for
     * 44 seconds. Configuring one widget must touch one widget.
     *
     * ⚠ **`getGlanceIdBy` throws for an id the system no longer knows**, which crashed a real
     * device at M15.6b: the widget can be removed while this screen is still open. The result is
     * already `RESULT_CANCELED`, which is the right answer for a widget that no longer exists.
     */
    private fun bind(
        appWidgetId: Int,
        measure: Measure,
    ) {
        lifecycleScope.launch {
            val glanceId =
                runCatching {
                    GlanceAppWidgetManager(this@MeasureWidgetConfigActivity).getGlanceIdBy(appWidgetId)
                }.getOrNull()
            if (glanceId == null) {
                finish()
                return@launch
            }
            updateAppWidgetState(
                context = this@MeasureWidgetConfigActivity,
                definition = PreferencesGlanceStateDefinition,
                glanceId = glanceId,
            ) { preferences ->
                preferences.toMutablePreferences().apply {
                    this[MeasureWidget.MEASURE_ID] = measure.id.value
                }
            }
            MeasureWidget().update(this@MeasureWidgetConfigActivity, glanceId)
            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}

@Composable
private fun MeasureWidgetConfigScreen(
    onPicked: (Measure) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MeasureWidgetConfigViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MeasureWidgetConfigContent(state = state, onPicked = onPicked, modifier = modifier)
}

@Composable
private fun MeasureWidgetConfigContent(
    state: MeasureWidgetConfigUiState,
    onPicked: (Measure) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().padding(Spacing.screenPadding)) {
        Text(
            text = stringResource(R.string.widget_measure_config_title),
            style = MaterialTheme.typography.headlineMedium,
            color = HabitsTheme.colors.text.primary,
        )
        when {
            // Nothing yet is not the same as nothing at all — see MeasureWidgetConfigUiState.
            !state.loaded -> {
                Unit
            }

            state.measures.isEmpty() -> {
                Text(
                    modifier = Modifier.padding(top = Spacing.cardGap),
                    text = stringResource(R.string.widget_measure_config_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = HabitsTheme.colors.text.secondary,
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.padding(top = Spacing.cardGap),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                ) {
                    items(items = state.measures, key = { measure -> measure.id.value }) { measure ->
                        MeasurePickerRow(measure = measure, onPicked = onPicked)
                    }
                }
            }
        }
    }
}

@Composable
private fun MeasurePickerRow(
    measure: Measure,
    onPicked: (Measure) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onPicked(measure) }
                .padding(vertical = Spacing.chipGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap),
    ) {
        HabitIcon(
            emoji = measure.emoji,
            name = measure.name,
            habitColor = measure.color.resolve(),
            size = HabitIconSize.SMALL,
        )
        Text(
            text = measure.name,
            style = MaterialTheme.typography.titleMedium,
            color = HabitsTheme.colors.text.primary,
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun MeasureWidgetConfigPreview() {
    HabitsTheme {
        MeasureWidgetConfigContent(
            state =
                MeasureWidgetConfigUiState(
                    measures =
                        listOf(
                            previewMeasure("Weight", "⚖️", "kg", HabitColor.GREEN),
                            previewMeasure("Sleep", "😴", "h", HabitColor.PURPLE),
                        ),
                    loaded = true,
                ),
            onPicked = {},
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun MeasureWidgetConfigEmptyPreview() {
    HabitsTheme {
        MeasureWidgetConfigContent(state = MeasureWidgetConfigUiState(loaded = true), onPicked = {})
    }
}

private fun previewMeasure(
    name: String,
    emoji: String,
    unit: String,
    color: HabitColor,
): Measure =
    Measure(
        id = MeasureId(name),
        name = name,
        emoji = emoji,
        unit = unit,
        color = color,
    )
