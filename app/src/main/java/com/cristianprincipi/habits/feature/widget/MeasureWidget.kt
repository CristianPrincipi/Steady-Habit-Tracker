package com.cristianprincipi.habits.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.unit.ColorProvider
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.SparklineColors
import com.cristianprincipi.habits.core.designsystem.component.SparklineMetrics
import com.cristianprincipi.habits.core.designsystem.component.sparklineBitmap
import com.cristianprincipi.habits.core.designsystem.theme.ChartOpacity
import com.cristianprincipi.habits.core.designsystem.theme.HabitsDarkColorScheme
import com.cristianprincipi.habits.core.designsystem.theme.HabitsDarkColors
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.ui.resolveIn
import com.cristianprincipi.habits.domain.usecase.GetMeasureAverages

/**
 * One tracked number's last thirty days on the home screen: a line, and the average beside it.
 *
 * ### ⚠⚠ Why this is not the habit widget with different data
 *
 * A dot grid encodes done / not-done, and a body weight has no such state — M15.6's own note, and
 * the reason the reference image does not cover this widget at all. What carries over is the
 * *chrome*: the same card, the same badge, the same two-line header, so a home screen holding both
 * reads as one app. What is different is the picture underneath and the sentence in the header.
 *
 * ### ⭐ The subtitle names the window, and it has to
 *
 * Thirty days is [GetMeasureAverages.MEASURE_WINDOW_DAYS] — the span Stats already averages over,
 * shared rather than restated so the line and the figure cannot describe different data. A widget
 * that printed *"84.3 kg"* alone would be read as today's weight, which is precisely what it is
 * not.
 *
 * ⚠ **Colours come from `HabitsDarkColors` directly and the metrics are literal**, for
 * `WidgetChrome`'s reason: Glance compiles to `RemoteViews`, so there is no composition here to
 * carry a theme.
 */
internal class MeasureWidget : GlanceAppWidget() {
    // Exact for HabitGridWidget's reason: the drawing scales to whatever size it is given, so the
    // real size is the only useful input and a bucketed one leaves a third of the card empty.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        // Resolved BEFORE provideContent so the opening frame is already right — see
        // loadMeasureWidgetState. This is the first frame only; the flow below carries the rest.
        val initial =
            loadMeasureWidgetState(
                context,
                getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[MEASURE_ID],
            )

        provideContent {
            // ⚠⚠ Re-read here, not closed over, because provideGlance runs once per Glance session
            // (ADR-129 §16). A widget that captured the resolved id showed whatever was true when
            // its session started, so configuring it did nothing visible for up to 45 seconds.
            val measureId = currentState(MEASURE_ID)
            val state by
                remember(measureId) { measureWidgetStateFlow(context, measureId) }
                    .collectAsState(initial)
            MeasureWidgetBody(state)
        }
    }

    internal companion object {
        /** The configured subject. Written by the configuration activity, read above. */
        val MEASURE_ID = stringPreferencesKey("measure_id")
    }
}

@Composable
private fun MeasureWidgetBody(state: MeasureWidgetState) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(HabitsDarkColorScheme.surfaceContainer))
                .cornerRadius(CARD_CORNER)
                .padding(CARD_PADDING),
    ) {
        when (state) {
            MeasureWidgetState.Unconfigured -> {
                WidgetMessage(R.string.widget_measure_unconfigured)
            }

            MeasureWidgetState.MeasureMissing -> {
                WidgetMessage(R.string.widget_measure_missing)
            }

            is MeasureWidgetState.Content -> {
                WidgetHeader(
                    emoji = state.emoji,
                    name = state.name,
                    subtitle = state.summary(),
                    color = state.color,
                )
                Spacer(GlanceModifier.height(HEADER_GAP))
                Box(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    // ⚠ An empty window is ORDINARY, not an error: it is a user who has not weighed
                    // themselves this month. It gets a plain sentence rather than a warning, and
                    // the header above it already says "—" rather than inventing a zero.
                    if (state.points.isEmpty()) {
                        WidgetMessage(R.string.widget_measure_no_readings)
                    } else {
                        Sparkline(state)
                    }
                }
            }
        }
    }
}

/**
 * The average and the window on one line — *"84.3 kg · last 30 days"*.
 *
 * ⭐ **The three value formats are Stats' own strings**, reused rather than restated: one decimal
 * place, the unit only when the user typed one, and an em dash when there is nothing to average.
 * Two surfaces printing the same mean to different precision would be a difference the reader would
 * take for a difference in the data.
 */
@Composable
private fun MeasureWidgetState.Content.summary(): String {
    val context = LocalContext.current
    val value =
        when {
            average == null -> context.getString(R.string.stats_measure_none)
            unit.isBlank() -> context.getString(R.string.stats_measure_value, average)
            else -> context.getString(R.string.stats_measure_value_with_unit, average, unit)
        }
    return context.getString(
        R.string.widget_measure_summary,
        value,
        GetMeasureAverages.MEASURE_WINDOW_DAYS,
    )
}

/**
 * The line, filling whatever the header left.
 *
 * ⭐ **It fills rather than centring, where the dot grid centres.** The grid has a view length worth
 * preserving — seven rows of a chosen dot size, so spare height is spare (ADR-129 §10) — while a
 * curve has no natural height at all: it is a shape, and giving it the whole box is what makes the
 * shape legible on a small widget.
 *
 * ⚠ **A widget can be resized below the header**, leaving nothing underneath. `SparklineMetrics`
 * refuses a non-positive box by invariant, so the case is checked here rather than caught.
 */
@Composable
private fun Sparkline(state: MeasureWidgetState.Content) {
    val density = LocalContext.current.resources.displayMetrics.density
    val size = LocalSize.current
    val widthDp = size.width.value - (CARD_PADDING.value * 2)
    val heightDp = size.height.value - (CARD_PADDING.value * 2) - BADGE_SIZE.value - HEADER_GAP.value
    if (widthDp <= 0f || heightDp <= 0f) return

    val line = state.color.resolveIn(HabitsDarkColors.habit)
    val metrics =
        SparklineMetrics(
            widthPx = widthDp * density,
            heightPx = heightDp * density,
            strokePx = Sizing.chartStroke.value * density,
        )
    val colors =
        SparklineColors(
            line = line.toArgb(),
            fillTop = line.copy(alpha = ChartOpacity.FILL_TOP).toArgb(),
            fillBottom = line.copy(alpha = ChartOpacity.FILL_BOTTOM).toArgb(),
        )
    // ⚠⚠ EVERY VALUE THE LAMBDA READS IS A KEY. ADR-130 §7 is why that sentence is here: the habit
    // grid read its colours inside `remember` without keying them, so a recolour moved the badge and
    // left the picture behind until the next resize. `colors` and `metrics` are both data classes,
    // so they compare by value and a recolour is a new key.
    val bitmap =
        remember(state.points, metrics, colors) {
            sparklineBitmap(points = state.points, metrics = metrics, colors = colors)
        }
    Image(provider = ImageProvider(bitmap), contentDescription = null)
}
