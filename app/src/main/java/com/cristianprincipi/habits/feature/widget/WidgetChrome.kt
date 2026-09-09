package com.cristianprincipi.habits.feature.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
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
 * The emoji badge, the name, and one line under it.
 *
 * @param subtitle already formatted by the caller, because the two widgets say different things
 *   there — *"3 of 7 this week"* against *"84.3 kg · last 30 days"* — and neither sentence is the
 *   header's business to build.
 */
@Composable
internal fun WidgetHeader(
    emoji: String,
    name: String,
    subtitle: String,
    color: HabitColor,
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
            Text(text = emoji, style = TextStyle(fontSize = BADGE_EMOJI_SIZE))
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
