package com.cristianprincipi.habits.feature.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.cristianprincipi.habits.MainActivity
import com.cristianprincipi.habits.core.designsystem.theme.HabitsDarkColorScheme
import com.cristianprincipi.habits.core.designsystem.theme.HabitsDarkColors
import com.cristianprincipi.habits.core.ui.resolveIn
import com.cristianprincipi.habits.domain.model.HabitColor

/*
 * What every home-screen widget in this app shares: its card metrics, its header, and its way of
 * saying nothing-to-draw.
 *
 * ⚠⚠ **Extracted at M15.6d rather than copied.** The measure widget is the habit widget's sibling
 * by design — the same card, the same badge, the same two lines of header, a different picture
 * underneath. Two copies of that chrome would agree on the day they were written and drift on the
 * first change to either, and the drift would be invisible until both widgets sat on one home
 * screen side by side, which is exactly where they will sit.
 *
 * ⚠ **These are literal `dp` outside `core/designsystem/theme/`, which `CLAUDE.md` §6 normally
 * forbids, and the exemption is M15.6b's**: there is no composition here to carry a `MaterialTheme`,
 * so a Glance widget cannot read a token. Colours come from `HabitsDarkColors` directly for the same
 * reason, and v1 is dark-only by decision.
 */

/**
 * The size the **picker preview** is composed at — M15.9, and shared so the two entries match.
 *
 * ⭐ **The declared target footprint, not a flattering one.** Both widget-info files ask for
 * `targetCellWidth=3`, `targetCellHeight=2`, and on the user's launcher (360dp wide, eight columns)
 * four cells measured 176dp — so three are ~132dp, and two rows ~107dp. Composing the preview at the
 * size the widget will actually land at is what makes the picture a promise rather than an advert:
 * at 132x107 the grid fits nineteen weeks, and nineteen weeks is what they get.
 *
 * ⚠ **A preview needs a size of its own because `SizeMode.Exact` is not a `PreviewSizeMode`.** Both
 * widgets are `Exact` — they draw to whatever they are given — and there is no widget yet when the
 * picker asks, so there is nothing for `Exact` to read. Verified against the Glance 1.2.0 AAR:
 * `Single` and `Responsive` implement the interface and `Exact` does not.
 */
internal val PREVIEW_SIZE = DpSize(132.dp, 107.dp)

/** The inset from the widget's card edge to anything drawn in it. */
internal val CARD_PADDING = 12.dp

/** The card's own corner. */
internal val CARD_CORNER = 16.dp

/** Between the header and the picture beneath it. */
internal val HEADER_GAP = 10.dp

internal val BADGE_SIZE = 36.dp
private val BADGE_CORNER = 18.dp
private val BADGE_GAP = 8.dp
private val TITLE_SIZE = 15.sp
private val SUBTITLE_SIZE = 12.sp
private val BADGE_EMOJI_SIZE = 16.sp

/** The wash the app's habit icon sits on, so a badge reads as its colour without competing. */
private const val BADGE_TINT = 0.15f

private const val MESSAGE_LINES = 3

/**
 * The card every widget in this app is drawn on, and the tap that opens the app on **Week**.
 *
 * ⭐⭐ **The tap is the user's request of 2026-09-09** — *"I would like to land on the Week page of
 * the app (the primary page), when I click on every widget. So I can have a quick opening directly
 * from the widgets."* ⚠ Before this there was no click handler on either widget at all: a tapped
 * card did nothing, rather than opening the app somewhere unhelpful.
 *
 * ⚠⚠ **It is on the shared card and not on the two widgets, for the reason this whole file exists.**
 * Two copies of a `clickable` would agree on the day they were written; the drift would show up as
 * one widget on a home screen opening the app while its sibling beside it does nothing, which reads
 * as a broken widget rather than as a missing line. ⭐ And a third widget cannot forget — it is the
 * same argument `NavGraphBuilder.screen<T>` makes for opaque destinations (ADR-122).
 *
 * ⚠ **The whole card is the target, including the two empty states.** A widget pointed at a habit
 * that has left the mapping is exactly when a user wants the app, and a message with no way into it
 * would be a dead end.
 *
 * @see MainActivity.landOnWeek for why the intent carries `CLEAR_TOP or SINGLE_TOP` rather than the
 *   one-line `CLEAR_TASK`.
 */
@Composable
internal fun WidgetCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(HabitsDarkColorScheme.surfaceContainer))
                .cornerRadius(CARD_CORNER)
                .clickable(actionStartActivity(MainActivity.landOnWeek(LocalContext.current)))
                .padding(CARD_PADDING),
        content = content,
    )
}

/**
 * The badge, the name, and one line under it.
 *
 * ⚠⚠ **The badge is not always an emoji, and that is M15.6g.** A habit's Notion property carries
 * one; a number's does not — `Weight` and `Sleep` are plain words — so the measure widget drew an
 * empty tinted circle while the picker that placed it drew `W`. The user's ruling was to put the
 * **average** in it instead, and to take the figure out of the subtitle so it is printed once. This
 * therefore takes a string and a size: the header does not know which of the two it is holding.
 *
 * @param subtitle already formatted by the caller, because the two widgets say different things
 *   there — *"3 of 7 this week"* against *"kg · last 30 days"* — and neither sentence is the
 *   header's business to build.
 * @param badgeTextSize defaulted for the emoji case, where one glyph at 16sp clears a 36dp circle at
 *   every font scale Android offers. A number does not — see `fitBadgeSp`.
 * @param badgeColor required, not defaulted — see the note above [Text] in the body.
 */
@Composable
internal fun WidgetHeader(
    badge: String,
    name: String,
    subtitle: String,
    color: HabitColor,
    badgeColor: ColorProvider,
    badgeTextSize: TextUnit = BADGE_EMOJI_SIZE,
) {
    val resolved = color.resolveIn(HabitsDarkColors.habit)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                GlanceModifier
                    .size(BADGE_SIZE)
                    .cornerRadius(BADGE_CORNER)
                    .background(ColorProvider(resolved.copy(alpha = BADGE_TINT))),
            contentAlignment = Alignment.Center,
        ) {
            // ⚠⚠ **EVERY `Text` IN A WIDGET MUST STATE ITS COLOUR, AND THIS ONE SHIPPED WITHOUT
            // ONE.** Glance compiles to `RemoteViews`, and `glance_text.xml` is a bare `TextView`
            // with no `android:textColor` — so a null `TextStyle.color` inherits the **host
            // launcher's** theme, which follows the system light/dark setting, while this card is
            // forced dark at every hour of the day. M15.6g's first attempt drew the average in the
            // launcher's default: on a light-mode phone that is near-black on #1C1C1F, and the user
            // saw a blank circle in the measure's colour. The three `Text`s below always set one;
            // this was the only one that did not, because an emoji does not need it.
            //
            // ⭐ So `badgeColor` is **required rather than defaulted**. An emoji ignores it — colour
            // emoji are drawn from their own font — so the habit widget loses nothing by stating it,
            // and no future caller can reach this line without having thought about it.
            //
            // maxLines so a value too long for its circle clips rather than stacking into two
            // half-height rows, which reads as a rendering fault. fitBadgeSp keeps it rare.
            Text(
                text = badge,
                style = TextStyle(color = badgeColor, fontSize = badgeTextSize),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.width(BADGE_GAP))
        Column {
            Text(
                text = name,
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
                text = subtitle,
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
 * ⚠ **Every empty state here is a sentence, not blank space.** A widget outlives the screen that
 * made it, so an empty card would read as a habit with no history — or a number never recorded —
 * rather than as a widget with nothing pointed at it.
 */
@Composable
internal fun WidgetMessage(textRes: Int) {
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
