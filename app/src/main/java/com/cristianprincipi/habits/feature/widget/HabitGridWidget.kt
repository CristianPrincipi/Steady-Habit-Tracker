package com.cristianprincipi.habits.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
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
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.DayStatus
import com.cristianprincipi.habits.core.designsystem.component.DotGridMetrics
import com.cristianprincipi.habits.core.designsystem.component.dotGridBitmap
import com.cristianprincipi.habits.core.designsystem.component.yearDayColor
import com.cristianprincipi.habits.core.designsystem.theme.HabitsDarkColorScheme
import com.cristianprincipi.habits.core.designsystem.theme.HabitsDarkColors
import com.cristianprincipi.habits.core.ui.resolveIn

/**
 * A habit's trailing year on the home screen, as the dot grid the app already draws.
 *
 * ⭐ **One resizable provider, not one per habit.** iOS ships discrete size families; Android ships
 * a provider that resizes plus a configuration activity that asks which habit. `SizeMode.Responsive`
 * supplies the two layouts the reference shows, and the narrow one simply holds fewer weeks — which
 * is the reference's own firmer fact (`docs/widget-reference/README.md`): the dot size is identical
 * in both, so half the width is half the history rather than smaller dots.
 *
 * ⚠ **The picture is a `Bitmap`, and that is forced.** Glance compiles to `RemoteViews`, so it has
 * no `Canvas`; see `drawDotGrid`'s KDoc for why the drawing is shared with `YearDotGrid` rather
 * than written twice.
 *
 * ⚠ **Colours come from `HabitsDarkColors` directly, not from a theme.** There is no composition
 * here to carry one, and v1 is dark-only by decision (`CLAUDE.md` §1).
 */
internal class HabitGridWidget : GlanceAppWidget() {
    // ⚠⚠ Exact, NOT Responsive — and this was a real defect on the user's phone, not a
    // preference. Responsive hands the composition the matched BUCKET size, so a 176x107dp widget
    // was laid out as the 160x120dp bucket and left a third of the card empty. Responsive earns its
    // keep when the layout must change SHAPE between sizes; this one does not — it holds more or
    // fewer weeks at whatever size it is given, so the real size is the only useful input.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        // Read and resolve BEFORE provideContent: loading is suspend and the composition is not, so
        // the alternative is a composable that blocks or one that renders empty and then corrects
        // itself — which on a widget is a visible flash of the wrong answer.
        val habitId = getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[HABIT_ID]
        val state = loadHabitWidgetState(context, habitId)
        provideContent { WidgetBody(state) }
    }

    internal companion object {
        /** The configured subject. Written by the configuration activity, read above. */
        val HABIT_ID = stringPreferencesKey("habit_id")
    }
}

@Composable
private fun WidgetBody(state: HabitWidgetState) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(HabitsDarkColorScheme.surfaceContainer))
                .cornerRadius(CARD_CORNER)
                .padding(CARD_PADDING),
    ) {
        when (state) {
            HabitWidgetState.Unconfigured -> {
                Message(R.string.widget_unconfigured)
            }

            HabitWidgetState.HabitMissing -> {
                Message(R.string.widget_habit_missing)
            }

            is HabitWidgetState.Content -> {
                Header(state)
                Spacer(GlanceModifier.height(HEADER_GAP))
                // ⭐ The grid takes the rest of the card and sits in the MIDDLE of it — the user's
                // choice of 2026-09-08, made against measured numbers. A seven-row grid cannot fill
                // a tall widget and keep small dots: at 492x225dp the dot wants to be 25dp, which
                // fills but buys only 15 weeks. They kept the ~31-week view, so the spare height is
                // split above and below rather than dumped at the bottom, where it read as a broken
                // layout rather than as padding.
                Box(
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    contentAlignment = Alignment.Center,
                ) {
                    Grid(state)
                }
            }
        }
    }
}

/**
 * ⚠ **Both empty states are sentences, not blank space.** A widget outlives the screen that made
 * it, so an empty grid would read as a habit with no history rather than as a widget with no habit.
 */
@Composable
private fun Message(textRes: Int) {
    Text(
        text = LocalContext.current.getString(textRes),
        style =
            TextStyle(
                color = ColorProvider(HabitsDarkColors.text.secondary),
                fontSize = SUBTITLE_SIZE,
            ),
        maxLines = MESSAGE_LINES,
    )
}

@Composable
private fun Header(state: HabitWidgetState.Content) {
    val context = LocalContext.current
    val habitColor = state.color.resolveIn(HabitsDarkColors.habit)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                GlanceModifier
                    .size(BADGE_SIZE)
                    .cornerRadius(BADGE_CORNER)
                    // The same wash the app's habit icon sits on, so the badge reads as the habit's
                    // colour without competing with the grid drawn in it at full strength.
                    .background(ColorProvider(habitColor.copy(alpha = BADGE_TINT))),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = state.emoji, style = TextStyle(fontSize = BADGE_EMOJI_SIZE))
        }
        Spacer(GlanceModifier.width(BADGE_GAP))
        Column {
            Text(
                text = state.name,
                style =
                    TextStyle(
                        color = ColorProvider(HabitsDarkColors.text.primary),
                        fontSize = TITLE_SIZE,
                        fontWeight = FontWeight.Bold,
                    ),
                // Ellipsised rather than wrapped: the narrow widget has one line's room, and the
                // reference truncates there too.
                maxLines = 1,
            )
            Text(
                text =
                    context.getString(
                        R.string.habit_card_summary,
                        state.completedThisWeek,
                        state.daysThisWeek,
                    ),
                style =
                    TextStyle(
                        color = ColorProvider(HabitsDarkColors.text.secondary),
                        fontSize = SUBTITLE_SIZE,
                    ),
                maxLines = 1,
            )
        }
    }
}

/**
 * The dot grid, sized to whatever width this instance was given.
 *
 * ⭐ **The dot size is fixed and the column count follows from the width**, not the other way round.
 * That is the reference measured rather than eyeballed (ADR-128 §4): both widgets draw dots at an
 * identical pitch, so the narrow one holds *fewer weeks*. Fixing the columns instead would shrink
 * the dots until the small widget was a different picture of the same year.
 */
@Composable
private fun Grid(state: HabitWidgetState.Content) {
    val density = LocalContext.current.resources.displayMetrics.density
    val size = LocalSize.current
    // What is left after the card's padding and the header above this.
    val availableWidth = size.width.value - (CARD_PADDING.value * 2)
    val availableHeight = size.height.value - (CARD_PADDING.value * 2) - BADGE_SIZE.value - HEADER_GAP.value
    val fit = fitGrid(availableWidth, availableHeight)
    val days = state.year.lastWeeks(fit.columns)

    val habitColor = state.color.resolveIn(HabitsDarkColors.habit)
    val tint = HabitsDarkColors.tint.yearDotIncomplete
    val bitmap =
        remember(days, fit, density) {
            dotGridBitmap(
                days = days,
                metrics =
                    DotGridMetrics(
                        dotPx = fit.dotDp * density,
                        gapPx = fit.gapDp * density,
                        radiusPx = (fit.dotDp / DOT_CORNER_RATIO) * density,
                    ),
            ) { status: DayStatus -> habitColor.yearDayColor(status, tint).toArgb() }
        }
    Image(provider = ImageProvider(bitmap), contentDescription = null)
}

private val CARD_PADDING = 12.dp
private val CARD_CORNER = 16.dp
private val HEADER_GAP = 10.dp
private val BADGE_SIZE = 36.dp
private val BADGE_CORNER = 18.dp
private val BADGE_GAP = 8.dp

/** A 6dp dot gets the design system's 1dp corner; the ratio holds that proportion at every size. */
private const val DOT_CORNER_RATIO = 6f
private val TITLE_SIZE = 15.sp
private val SUBTITLE_SIZE = 12.sp
private val BADGE_EMOJI_SIZE = 16.sp
private const val BADGE_TINT = 0.15f
private const val MESSAGE_LINES = 3
