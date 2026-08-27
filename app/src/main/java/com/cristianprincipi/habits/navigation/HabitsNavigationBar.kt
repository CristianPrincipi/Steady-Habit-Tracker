package com.cristianprincipi.habits.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.icon.LucideIcon
import com.cristianprincipi.habits.core.designsystem.icon.LucideStroke
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * The five-tab bottom bar — `docs/design-spec.md` §1.
 *
 * Every tab owns a colour and shows it **only when active**; inactive tabs are white at 22%. The
 * active one also draws its glyph at a heavier stroke (2.2 against 1.5) and a 4dp dot beneath its
 * label. All three signals are the design's, and all three are tokens rather than numbers typed
 * here (`CLAUDE.md` §6).
 *
 * ⭐ **The design's `backdrop-blur-xl` is not implemented, and that is now a decision rather than a
 * gap** — the user looked at the flat bar in M5.6 and accepted it (ADR-066). A true backdrop blur
 * needs `RenderEffect` (API 31+, and `minSdk` is 26) or a third-party modifier, and `CLAUDE.md` §8
 * says to ask before adding a dependency.
 *
 * So the opaque `#111113` bar — `colorScheme.surface` — is **the v1 design, at every API level**,
 * not a fallback awaiting the real thing. Do not go looking for a blur to finish. If `minSdk` ever
 * reaches 31 the blur becomes free, and ADR-066 is the note saying it was wanted.
 */
@Composable
fun HabitsNavigationBar(
    selected: HabitsTab,
    onSelect: (HabitsTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // The design's hairline above the bar. `outlineVariant` is the 6% white the whole app uses
        // for card borders and this divider alike (`docs/tokens.md` §1).
        HorizontalDivider(
            thickness = Sizing.hairlineBorder,
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        // `tonalElevation` is left at its default and does nothing here: this theme pins
        // `surfaceTint` to transparent (`Color.kt`), so Material has no colour to raise the
        // container with. Saying so is cheaper than a zero somebody later reads as a mistake.
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
            HabitsTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val activeColor = tab.activeColor(HabitsTheme.colors)
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onSelect(tab) },
                    icon = {
                        LucideIcon(
                            glyph = tab.glyph,
                            // The label directly beneath says the same word, so announcing the glyph
                            // too would make a screen reader read every tab twice. `NavigationBarItem`
                            // already carries the selected state and the label into semantics.
                            contentDescription = null,
                            modifier = Modifier.size(Sizing.iconLarge),
                            strokeWidth =
                                if (isSelected) LucideStroke.NAV_ACTIVE else LucideStroke.NAV_INACTIVE,
                        )
                    },
                    label = { TabLabel(tab = tab, isSelected = isSelected, activeColor = activeColor) },
                    colors =
                        NavigationBarItemDefaults.colors(
                            selectedIconColor = activeColor,
                            selectedTextColor = activeColor,
                            unselectedIconColor = HabitsTheme.colors.text.helper,
                            unselectedTextColor = HabitsTheme.colors.text.helper,
                            // Material's pill behind the active icon is not in this design, and the
                            // 4dp dot below the label is what replaces it.
                            indicatorColor = Color.Transparent,
                        ),
                )
            }
        }
    }
}

/**
 * A tab's label, and the 4dp dot that marks the active one.
 *
 * The dot lives in the label slot because `NavigationBarItem` has no slot beneath it. It is drawn as
 * a transparent box when inactive rather than removed, so the row does not change height as the
 * selection moves — the same reason M4.18's "Sync now" is disabled rather than hidden (ADR-060).
 */
@Composable
private fun TabLabel(
    tab: HabitsTab,
    isSelected: Boolean,
    activeColor: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.tabIndicatorGap),
    ) {
        Text(
            text = stringResource(tab.label),
            style = MaterialTheme.typography.labelSmall,
        )
        Box(
            modifier =
                Modifier
                    .size(Sizing.navIndicatorDot)
                    .clip(CircleShape)
                    .background(if (isSelected) activeColor else Color.Transparent),
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun HabitsNavigationBarPreview() {
    HabitsTheme {
        HabitsNavigationBar(selected = HabitsTab.WEEK, onSelect = {})
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun HabitsNavigationBarMonthPreview() {
    HabitsTheme {
        HabitsNavigationBar(selected = HabitsTab.MONTH, onSelect = {})
    }
}
