package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * The surface every card in the app sits on.
 *
 * One shape, one fill, one hairline border, repeated on the Month, Week, Year, stat and settings
 * cards — so it is written once here rather than five times. `#1c1c1f` at a 16dp radius with a 6%
 * white border (`docs/tokens.md §1.1`, `§3`).
 *
 * @param contentPadding [Spacing.cardPadding] everywhere except the Month card, which is tighter at
 *   [Spacing.cardPaddingCompact] — the one exception in the design.
 * @param verticalArrangement the gap between the card's own sections.
 */
@Composable
fun HabitsCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = Spacing.cardPadding,
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(Spacing.cardGap),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(Sizing.hairlineBorder, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            verticalArrangement = verticalArrangement,
            content = content,
        )
    }
}

/**
 * An uppercase, widely tracked caption above a section — `YEAR OVERVIEW`, `MONTHLY COMPLETIONS`.
 *
 * The component uppercases nothing: `text-transform` is a rendering concern in CSS but a data
 * concern here, and uppercasing in the composable would mangle locales whose casing rules differ.
 * Pass the string already cased, from `strings.xml`.
 */
@Composable
fun SectionEyebrow(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = HabitsTheme.textStyles.eyebrow,
        color = HabitsTheme.colors.text.eyebrow,
    )
}

@Preview(backgroundColor = 0xFF111113, showBackground = true)
@Composable
private fun HabitsCardPreview() {
    HabitsTheme {
        HabitsCard(modifier = Modifier.padding(Spacing.screenPadding)) {
            SectionEyebrow(text = "YEAR OVERVIEW")
            Text(text = "Card content", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
