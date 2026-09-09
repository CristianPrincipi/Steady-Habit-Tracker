package com.cristianprincipi.habits.feature.widget

import android.content.Context
import android.graphics.Paint
import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
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
 * One tracked number's last thirty days on the home screen: a line, and the average above it.
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
                val badge = state.badge()
                WidgetHeader(
                    badge = badge,
                    // ⚠ White, NOT the measure's colour, and that is measured rather than chosen:
                    // the number is the widget's only printing of the value, so it is text and owes
                    // AA — and the colour on its own 15% wash is 3.38:1 for purple and 3.24:1 for
                    // red. The circle still carries the colour; the figure on it has to be legible.
                    // See ContrastTest.theMeasureBadgesNumberTakesTheWhiteThatPasses.
                    badgeColor = ColorProvider(HabitsDarkColors.text.primary),
                    name = state.name,
                    subtitle = state.summary(),
                    color = state.color,
                    badgeTextSize = badgeTextSize(badge),
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
 * The average, in the badge the emoji left empty — *"84.3"*.
 *
 * ⭐⭐ **The user's call (M15.6g), and the reason is that a number property has no emoji.**
 * `Weight` and `Sleep` are plain words in Notion, so this circle drew empty — while the picker that
 * placed the widget drew `W`, because `HabitIcon` applies `habitGlyph`'s initial fallback and
 * `WidgetHeader` never did. Offered the initial, the figure or nothing, they chose the figure **on
 * condition it is printed once**: it moves here and leaves [summary], rather than appearing twice a
 * centimetre apart at two precisions.
 *
 * ⭐ **Stats' own value strings**, reused rather than restated: one decimal place, and an em dash
 * when there is nothing to average. Two surfaces printing the same mean to different precision would
 * be a difference the reader would take for a difference in the data.
 *
 * ⚠ **The unit is deliberately not here.** `84.3 kg` fits a 36dp circle only by shrinking until
 * neither half reads, and the unit is the half that never changes — so it goes on the line below,
 * beside the window it already shares a sentence with.
 */
@Composable
private fun MeasureWidgetState.Content.badge(): String {
    val context = LocalContext.current
    return when (average) {
        null -> context.getString(R.string.stats_measure_none)
        else -> context.getString(R.string.stats_measure_value, average)
    }
}

/**
 * The size that number draws at, measured against the circle it has to fit.
 *
 * ⚠ **Not a constant, because two things move it**: a longer value — a measure counted in steps
 * rather than kilograms — and the user's font scale, which grows text against a `dp` circle that
 * stays where it is. `fitBadgeSp` returns [MAX_BADGE_SP] untouched whenever the value already fits,
 * so nothing moves at the size and scale the design was drawn for.
 *
 * ⚠ A bare [Paint] carries the platform's default typeface, which is what Glance's `Text` renders
 * with. It is the same measurement the widget will make, not a model of it.
 */
@Composable
private fun badgeTextSize(text: String): TextUnit {
    val resources = LocalContext.current.resources
    val metrics = resources.displayMetrics
    val fontScale = resources.configuration.fontScale
    val paint = remember { Paint() }
    return remember(text, metrics.density, fontScale) {
        val availablePx = BADGE_SIZE.value * metrics.density * BADGE_TEXT_WIDTH_RATIO
        fitBadgeSp(availablePx) { sp ->
            paint.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, metrics)
            paint.measureText(text)
        }.sp
    }
}

/**
 * The unit and the window on one line — *"kg · last 30 days"*, or *"Last 30 days"* for a measure
 * the user gave no unit.
 *
 * ⭐ **The window is still named, and the reason survived the figure moving out of this line.**
 * Thirty days is [GetMeasureAverages.MEASURE_WINDOW_DAYS], the only span in this app that is not the
 * trailing year, and a badge reading `84.3` above a bare `kg` would be read as today's weight —
 * which is precisely what it is not.
 */
@Composable
private fun MeasureWidgetState.Content.summary(): String {
    val context = LocalContext.current
    return when {
        unit.isBlank() -> {
            context.getString(R.string.widget_measure_window, GetMeasureAverages.MEASURE_WINDOW_DAYS)
        }

        else -> {
            context.getString(
                R.string.widget_measure_summary,
                unit,
                GetMeasureAverages.MEASURE_WINDOW_DAYS,
            )
        }
    }
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
