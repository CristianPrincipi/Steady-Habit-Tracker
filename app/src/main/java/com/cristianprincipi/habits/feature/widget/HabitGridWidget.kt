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
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.unit.ColorProvider
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.DayStatus
import com.cristianprincipi.habits.core.designsystem.component.DotGridMetrics
import com.cristianprincipi.habits.core.designsystem.component.dotGridBitmap
import com.cristianprincipi.habits.core.designsystem.component.yearDayColor
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
        // itself — which on a widget is a visible flash of the wrong answer. This is the FIRST
        // frame only; everything after it comes from the reactive read below.
        val initial =
            loadHabitWidgetState(
                context,
                getAppWidgetState(context, PreferencesGlanceStateDefinition, id)[HABIT_ID],
            )

        provideContent {
            // ⚠⚠ THE HABIT ID IS RE-READ HERE, AND THIS FUNCTION RUNNING ONCE IS WHY (ADR-129 §16).
            // `runGlance` calls provideGlance exactly once per Glance session and holds the result
            // behind a `remember`, while `provideContent` returns Nothing — so a widget that closed
            // over the resolved value showed whatever was true when its session STARTED, for the
            // life of that session. Configuring a widget therefore did nothing visible:
            // `update()` on a live session sends UpdateGlanceState, which recomposes and re-pushes
            // the identical content rather than re-running the read above. The widget corrected
            // itself only when the session died of Glance's 45-second idle timeout and a new one
            // began — which is exactly the 40–50s the user reported.
            //
            // `currentState` reads LocalState, which is what UpdateGlanceState actually moves, so
            // the value below changes the moment the configuration activity writes it.
            val habitId = currentState(HABIT_ID)
            // ⭐ M15.6c: and the flow is why a SECOND check-off inside 45 seconds moves the grid.
            // `update()` on a live session only re-pushes what this function already resolved, so
            // without a live read the widget would go stale for exactly as long as it had most
            // recently been updated (ADR-130). `initial` is the seed, so the opening frame is the
            // resolved answer rather than a placeholder, and it is kept across a habitId change —
            // `collectAsState` remembers its value without keys — so switching habits shows the
            // previous grid for the ~25ms of the query instead of an empty card.
            val state by
                remember(habitId) { habitWidgetStateFlow(context, habitId) }
                    .collectAsState(initial)
            WidgetBody(state)
        }
    }

    internal companion object {
        /** The configured subject. Written by the configuration activity, read above. */
        val HABIT_ID = stringPreferencesKey("habit_id")
    }
}

@Composable
private fun WidgetBody(state: HabitWidgetState) {
    WidgetCard {
        when (state) {
            HabitWidgetState.Unconfigured -> {
                WidgetMessage(R.string.widget_unconfigured)
            }

            HabitWidgetState.HabitMissing -> {
                WidgetMessage(R.string.widget_habit_missing)
            }

            is HabitWidgetState.Content -> {
                WidgetHeader(
                    badge = state.emoji,
                    // Stated though an emoji ignores it — WidgetChrome requires it so no widget can
                    // draw text in whatever colour the launcher's theme happens to hand it.
                    badgeColor = ColorProvider(HabitsDarkColors.text.primary),
                    name = state.name,
                    subtitle =
                        LocalContext.current.getString(
                            R.string.habit_card_summary,
                            state.completedThisWeek,
                            state.daysThisWeek,
                        ),
                    color = state.color,
                )
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
    // ⚠⚠ **EVERY VALUE THE LAMBDA READS IS A KEY, AND THE COLOURS WERE MISSING** — found by the user
    // on 2026-09-08, and only because M15.6c made a recolour reach a live widget at all (ADR-130 §7).
    // With `habitColor` read but not keyed, changing a habit's colour recomposed `Header` — so the
    // badge went green immediately — while `Grid` handed back the bitmap it had already drawn. The
    // grid caught up only on a **resize**, because that moves `fit`. ⭐ `tint` is keyed for the same
    // rule even though it cannot move today: v1 is dark-only, and a light theme is deferred rather
    // than cancelled (`CLAUDE.md` §1), which is exactly when an unkeyed read becomes a second bug.
    val bitmap =
        remember(days, fit, density, habitColor, tint) {
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

/** A 6dp dot gets the design system's 1dp corner; the ratio holds that proportion at every size. */
private const val DOT_CORNER_RATIO = 6f
