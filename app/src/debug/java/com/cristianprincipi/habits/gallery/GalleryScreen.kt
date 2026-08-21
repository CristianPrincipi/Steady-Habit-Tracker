package com.cristianprincipi.habits.gallery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.component.SectionEyebrow
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * The gallery's contents, as a list of named sections.
 *
 * A `LazyColumn` rather than a scrolling `Column`: the gallery draws several hundred dots and two
 * full years of grid, and composing all of it for every frame would make the very components it
 * exists to show look slower than they are.
 */
@Composable
fun GalleryScreen(modifier: Modifier = Modifier) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = Spacing.screenPadding,
                end = Spacing.screenPadding,
                top = insets.calculateTopPadding() + Spacing.screenPadding,
                bottom = insets.calculateBottomPadding() + Spacing.bottomNavClearance,
            ),
        verticalArrangement = Arrangement.spacedBy(Spacing.settingsSectionGap),
    ) {
        item {
            Text(
                text = "Design system",
                style = MaterialTheme.typography.headlineSmall,
                color = HabitsTheme.colors.text.primary,
            )
        }
        items(gallerySections(), key = { it.title }) { section ->
            GallerySection(section)
        }
    }
}

/** One titled block of the gallery. */
data class GalleryEntry(
    val title: String,
    val content: @Composable () -> Unit,
)

@Composable
private fun GallerySection(section: GalleryEntry) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        SectionEyebrow(text = section.title)
        section.content()
    }
}

/**
 * A caption under a specimen, naming what it is.
 *
 * Developer copy, deliberately a literal — see the note in `src/debug/res/values/strings.xml`.
 */
@Composable
fun GalleryLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = HabitsTheme.colors.text.helper,
    )
}

/**
 * A caption that needs to be read, not skimmed — the two numeral variants, where the point of the
 * gallery is a decision rather than a check.
 */
@Composable
fun GalleryNote(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        modifier = modifier.padding(top = Spacing.weekPillGap),
        style = MaterialTheme.typography.bodySmall,
        color = HabitsTheme.colors.text.secondary,
    )
}

private fun gallerySections(): List<GalleryEntry> =
    listOf(
        GalleryEntry("COLOUR") { ColourSection() },
        GalleryEntry("TYPE") { TypeSection() },
        GalleryEntry("SHAPE AND SPACING") { ShapeSection() },
        GalleryEntry("ICONS") { IconSection() },
        GalleryEntry("HABIT ICON") { HabitIconSection() },
        GalleryEntry("CHIPS AND NAV") { ChipsAndNavSection() },
        GalleryEntry("WEEK PILLS") { WeekPillsSection() },
        GalleryEntry("DOT GRIDS") { DotGridSection() },
        GalleryEntry("STAT TILES — NUMERALS") { StatTileSection() },
        GalleryEntry("HABIT CARDS") { HabitCardSection() },
        GalleryEntry("EMPTY STATES") { EmptyStateSection() },
    )

@Preview(backgroundColor = 0xFF111113, showBackground = true, heightDp = 1400, widthDp = 400)
@Composable
private fun GalleryScreenPreview() {
    HabitsTheme { GalleryScreen() }
}
