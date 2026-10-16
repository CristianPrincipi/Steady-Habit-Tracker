package com.cristianprincipi.habits.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.unit.ColorProvider
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.component.DayStatus
import com.cristianprincipi.habits.core.designsystem.component.DotGridMetrics
import com.cristianprincipi.habits.core.designsystem.component.dotGridBitmap
import com.cristianprincipi.habits.core.designsystem.component.yearDayColor
import com.cristianprincipi.habits.core.designsystem.theme.HabitsDarkColorScheme
import com.cristianprincipi.habits.core.designsystem.theme.HabitsDarkColors
import com.cristianprincipi.habits.core.ui.resolveIn
import com.cristianprincipi.habits.domain.model.HabitColor

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
        // M15.11: the corner square means "today", so the widget must hear about midnight. Armed on
        // every session start — see MidnightRepaint.kt for why here and not in the app.
        armMidnightRepaint(context)

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

    // See [providePreview]. Exact cannot serve a preview — there is no widget to be exact about.
    override val previewSizeMode = SizeMode.Responsive(setOf(PREVIEW_SIZE))

    /**
     * What the **widget picker** draws — M15.9.
     *
     * ⭐⭐ **It is the real [WidgetBody], handed a sample state.** That is the whole reason this
     * exists rather than a drawable: a picture of the widget would have to be redrawn by hand every
     * time the card, the badge or the grid moved, and the drift would be invisible until someone
     * opened the picker. There is no second drawing here to fall out of step — the preview *is* the
     * widget, with `sampleHabitWidgetState` in place of `loadHabitWidgetState`.
     *
     * ⚠ **The default implementation is a silent no-op, not an error** (checked against the Glance
     * 1.2.0 AAR, `providePreview$suspendImpl` returns `Unit`). A widget that forgets to override this
     * previews nothing at all and says nothing about it, which is precisely how both of these
     * shipped in M15.6 with the picker falling back to the app icon.
     *
     * @param widgetCategory deliberately unused: this app declares `home_screen` only, so there is
     *   no second category to draw differently for.
     */
    override suspend fun providePreview(
        context: Context,
        widgetCategory: Int,
    ) {
        val sample = sampleHabitWidgetState(context)
        provideContent { WidgetBody(sample) }
    }

    internal companion object {
        /** The configured subject. Written by the configuration activity, read above. */
        val HABIT_ID = stringPreferencesKey("habit_id")
    }
}

@Composable
private fun WidgetBody(state: HabitWidgetState) {
    val content = state as? HabitWidgetState.Content
    val today = content?.today
    val size = LocalSize.current
    // One fit for both: the square and the room the header text keeps for it must agree.
    val square = fitTodaySquare(size.width.value, size.height.value)
    // Only a habit that is really there gets a square: the two empty states are sentences about a
    // widget with nothing to check off.
    val corner: (@Composable () -> Unit)? =
        if (content != null && today != null) {
            { TodaySquare(check = today, name = content.name, color = content.color, fit = square) }
        } else {
            null
        }
    WidgetCard(corner = corner) {
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
                    textEndInset = if (state.today != null) square.textEndInsetDp.dp else 0.dp,
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
    val fit = fitHabitWidgetGrid(size.width.value, size.height.value)
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

/**
 * Today, as a square in the card's top-right corner, that checks the habit off — M15.11.
 *
 * ⭐ The two states the request asked for: an **outline in the habit's colour** while
 * today is open, **filled with that colour** once it is done. A second tap takes it back, because a
 * control on a home screen gets brushed by accident and has to be undoable where it was hit.
 *
 * ⭐ **It is today's dot, drawn large.** The corner is the grid's own ratio ([DOT_CORNER_RATIO]), so
 * the square reads as the last dot of the grid lifted into the corner, not as a borrowed checkbox.
 *
 * ### ⭐ It mirrors the habit badge, and that is the user's call
 *
 * The badge sits [CARD_PADDING] in from the left, with [BADGE_GAP] to the text. The square sits on the
 * right with the same gap back to the text, **centred on the badge's own line** — so the header reads
 * badge · text · square, symmetric about its middle. Two earlier cuts were rejected on the phone: one
 * beside the name's top line, and one tucked into the card's corner padding, which lined up with
 * nothing else on the card.
 *
 * ⭐ **Its size and place come from the card ([fit], `SquareFit.kt`)**: 10dp on [CARD_PADDING] on a
 * short card, growing with the grid's dots to 13dp, 15dp in, on a 2x2 and everything bigger — the
 * size the user picked off their own 2x2 widgets. A fixed 10dp was smaller than a single dot on a
 * big card, and the user saw it (ADR-153).
 *
 * ⚠ **The cost is the whole text column, not just the name line**, because a square on the badge's
 * centre line straddles both lines: [SquareFit.textEndInsetDp]. The square grows only while the name
 * keeps its room. `TodaySquareFitTest`.
 *
 * ⚠ **The outline is two boxes, because Glance has no border.** A filled square with the card's own
 * colour inset by the stroke draws the same picture on every API level this app supports.
 *
 * ⚠ **The touch target is much bigger than the square**: the header's full height plus the top
 * padding ([SQUARE_TARGET_HEIGHT]), and everything right of the text — that is why the square is a
 * [WidgetCard] corner rather than one more item in the header row, which could not reach the padding.
 * It overlaps the text by only a few dp, so a tap on a name still opens the app.
 */
@Composable
private fun TodaySquare(
    check: TodayCheck,
    name: String,
    color: HabitColor,
    fit: SquareFit,
) {
    val size = fit.sizeDp.dp
    val stroke = fit.strokeDp.dp
    val habitColor = ColorProvider(color.resolveIn(HabitsDarkColors.habit))
    // Says what a tap DOES, not only what is shown: "checked" alone would not tell a TalkBack user
    // that this square writes.
    val description =
        LocalContext.current.getString(
            if (check.completed) R.string.widget_today_done else R.string.widget_today_open,
            name,
        )
    Box(
        modifier =
            GlanceModifier
                .size(fit.targetWidthDp.dp, SQUARE_TARGET_HEIGHT)
                .clickable(checkOffTodayAction(check))
                .semantics { contentDescription = description }
                .padding(top = CARD_PADDING + (BADGE_SIZE - size) / 2, end = fit.endDp.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        Box(
            modifier =
                GlanceModifier
                    .size(size)
                    .cornerRadius(size / DOT_CORNER_RATIO)
                    .background(habitColor),
            contentAlignment = Alignment.Center,
        ) {
            if (!check.completed) {
                Box(
                    modifier =
                        GlanceModifier
                            .size(size - stroke * 2)
                            .cornerRadius((size / DOT_CORNER_RATIO - stroke).coerceAtLeast(0.dp))
                            .background(ColorProvider(HabitsDarkColorScheme.surfaceContainer)),
                ) {}
            }
        }
    }
}

/** A 6dp dot gets the design system's 1dp corner; the ratio holds that proportion at every size. */
private const val DOT_CORNER_RATIO = 6f

/** The square's touch target reaches from the card's top edge to the header's bottom: 48dp. */
internal val SQUARE_TARGET_HEIGHT = CARD_PADDING + BADGE_SIZE
