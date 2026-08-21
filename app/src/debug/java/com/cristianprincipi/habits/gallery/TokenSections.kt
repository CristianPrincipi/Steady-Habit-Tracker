package com.cristianprincipi.habits.gallery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cristianprincipi.habits.core.designsystem.icon.Lucide
import com.cristianprincipi.habits.core.designsystem.icon.LucideIcon
import com.cristianprincipi.habits.core.designsystem.icon.LucideStroke
import com.cristianprincipi.habits.core.designsystem.theme.HabitsCorners
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/*
 * The token half of the gallery: colour, type, shape and icons.
 *
 * Every label here is a literal — developer copy naming a token, never localised, never shipped.
 * See `src/debug/res/values/strings.xml`.
 */

@Composable
fun ColourSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        GalleryLabel("Habit palette — the four a habit can be assigned")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            Swatch(HabitsTheme.colors.habit.blue, "blue")
            Swatch(HabitsTheme.colors.habit.green, "green")
            Swatch(HabitsTheme.colors.habit.orange, "orange")
            Swatch(HabitsTheme.colors.habit.purple, "purple")
        }

        GalleryLabel("Not assignable — amber is chart-5, grey is the Settings tab")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            Swatch(HabitsTheme.colors.habit.amber, "amber")
            Swatch(HabitsTheme.colors.habit.grey, "grey")
        }

        GalleryLabel("Surfaces")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            Swatch(MaterialTheme.colorScheme.background, "background")
            Swatch(MaterialTheme.colorScheme.surfaceContainer, "card")
            Swatch(MaterialTheme.colorScheme.surfaceVariant, "secondary")
            Swatch(MaterialTheme.colorScheme.surfaceContainerHighest, "switch")
        }

        GalleryLabel("Semantic")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            Swatch(MaterialTheme.colorScheme.primary, "primary")
            Swatch(HabitsTheme.colors.success, "success")
            Swatch(HabitsTheme.colors.warning, "warning")
            Swatch(MaterialTheme.colorScheme.error, "error")
        }

        GalleryLabel("Text opacity ramp — every foreground is white at one of these")
        TextRamp()
    }
}

@Composable
private fun Swatch(
    color: Color,
    name: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(SWATCH)
                    .background(color, MaterialTheme.shapes.medium)
                    .border(
                        Sizing.hairlineBorder,
                        MaterialTheme.colorScheme.outlineVariant,
                        MaterialTheme.shapes.medium,
                    ),
        )
        GalleryLabel(name)
    }
}

@Composable
private fun TextRamp() {
    val text = HabitsTheme.colors.text
    Column {
        RampRow("100% primary", text.primary)
        RampRow("55% property chip", text.propertyChip)
        RampRow("50% status label", text.statusLabel)
        RampRow("40% nav label", text.navLabel)
        RampRow("35% secondary", text.secondary)
        RampRow("30% tertiary", text.tertiary)
        RampRow("25% eyebrow", text.eyebrow)
        RampRow("22% helper", text.helper)
        RampRow("15% placeholder", text.placeholder)
    }
}

@Composable
private fun RampRow(
    name: String,
    color: Color,
) {
    Text(
        text = name,
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

@Composable
fun TypeSection() {
    val styles = HabitsTheme.textStyles
    Column {
        Specimen("displaySmall · 30/700", MaterialTheme.typography.displaySmall)
        Specimen("headlineSmall · 24/700 tight", MaterialTheme.typography.headlineSmall)
        Specimen("titleLarge · 20/600", MaterialTheme.typography.titleLarge)
        Specimen("titleMedium · 16/600", MaterialTheme.typography.titleMedium)
        Specimen("titleSmall · 14/600", MaterialTheme.typography.titleSmall)
        Specimen("bodyLarge · 16/400", MaterialTheme.typography.bodyLarge)
        Specimen("bodyMedium · 14/400", MaterialTheme.typography.bodyMedium)
        Specimen("bodySmall · 12/400", MaterialTheme.typography.bodySmall)
        Specimen("labelLarge · 14/600", MaterialTheme.typography.labelLarge)
        Specimen("labelMedium · 12/600", MaterialTheme.typography.labelMedium)
        Specimen("labelSmall · 10/600", MaterialTheme.typography.labelSmall)
        Specimen("eyebrow · 10/600 widest", styles.eyebrow, "SECTION EYEBROW")
        Specimen("eyebrowSmall · 9/600 widest", styles.eyebrowSmall, "COMPLETION")
        Specimen("weekdayInitial · 10/500 wide", styles.weekdayInitial, "MON TUE WED")
        Specimen("mono · 14/400 DM Mono", styles.mono, "secret_xxxxxxxxxxxx")
        Specimen("monoSmall · 10/400 DM Mono", styles.monoSmall, "Habit name")
    }
}

@Composable
private fun Specimen(
    name: String,
    style: TextStyle,
    sample: String = "Habits 0123456789",
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GalleryLabel(name)
        Text(text = sample, style = style, color = HabitsTheme.colors.text.primary)
    }
}

@Composable
fun ShapeSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        GalleryLabel("Radii — note large is 14dp, not Tailwind's 12dp")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            ShapeSwatch(MaterialTheme.shapes.extraSmall, "6")
            ShapeSwatch(MaterialTheme.shapes.small, "8")
            ShapeSwatch(MaterialTheme.shapes.medium, "10")
            ShapeSwatch(MaterialTheme.shapes.large, "14")
            ShapeSwatch(MaterialTheme.shapes.extraLarge, "16")
            ShapeSwatch(HabitsCorners.pill, "pill")
        }

        GalleryLabel("Spacing — 2, 3, 6, 8, 12, 14, 16, 20")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            listOf(
                Spacing.yearDotGap,
                Spacing.monthDotGap,
                Spacing.weekPillGap,
                Spacing.chipGap,
                Spacing.cardGap,
                Spacing.cardPaddingCompact,
                Spacing.cardPadding,
                Spacing.settingsSectionGap,
            ).forEach { SpacingBar(it) }
        }
    }
}

@Composable
private fun ShapeSwatch(
    shape: Shape,
    name: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .size(SWATCH)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape),
        )
        GalleryLabel(name)
    }
}

@Composable
private fun SpacingBar(value: Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier =
                Modifier
                    .width(value)
                    .height(SWATCH)
                    .background(HabitsTheme.colors.habit.blue, MaterialTheme.shapes.extraSmall),
        )
        GalleryLabel("${value.value.toInt()}")
    }
}

@Composable
fun IconSection() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
        GalleryLabel("All thirteen — Lucide, one family (ADR-024), 18dp")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            Lucide.all.take(SEVEN).forEach { GalleryIcon(it, Sizing.iconMedium) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            Lucide.all.drop(SEVEN).forEach { GalleryIcon(it, Sizing.iconMedium) }
        }

        GalleryLabel("Bottom nav, 22dp — active stroke 2.2 above, inactive 1.5 below")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            NavGlyphs.forEach {
                LucideIcon(
                    glyph = it,
                    contentDescription = null,
                    modifier = Modifier.size(Sizing.iconLarge),
                    tint = HabitsTheme.colors.habit.blue,
                    strokeWidth = LucideStroke.NAV_ACTIVE,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            NavGlyphs.forEach {
                LucideIcon(
                    glyph = it,
                    contentDescription = null,
                    modifier = Modifier.size(Sizing.iconLarge),
                    tint = HabitsTheme.colors.text.helper,
                    strokeWidth = LucideStroke.NAV_INACTIVE,
                )
            }
        }
        GalleryNote(
            "The two rows above are the same five glyphs at different stroke weights. That is the " +
                "pair a static drawable could not have given us, and the reason the icons are built " +
                "in Kotlin (ADR-025).",
        )
    }
}

@Composable
private fun GalleryIcon(
    glyph: com.cristianprincipi.habits.core.designsystem.icon.LucideGlyph,
    size: Dp,
) {
    LucideIcon(
        glyph = glyph,
        contentDescription = null,
        modifier = Modifier.size(size),
        tint = HabitsTheme.colors.text.primary,
    )
}

private val NavGlyphs =
    listOf(
        Lucide.LayoutGrid,
        Lucide.TextAlignJustify,
        Lucide.Calendar,
        Lucide.ChartNoAxesColumn,
        Lucide.Settings,
    )

private val SWATCH = 44.dp
private const val SEVEN = 7
