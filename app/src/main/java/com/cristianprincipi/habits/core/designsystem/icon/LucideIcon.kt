package com.cristianprincipi.habits.core.designsystem.icon

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Sizing
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * Builds an [ImageVector] for a glyph at a given stroke width.
 *
 * **The stroke width is why these are built in Kotlin rather than shipped as XML drawables.** The
 * bottom nav draws the same five glyphs at 2.2 when selected and 1.5 when not (`docs/tokens.md §5`),
 * and a vector drawable bakes its stroke in — that would mean two files per nav icon, with the
 * design's stroke ratio duplicated across ten files instead of stated once. `ImageVector.Builder`
 * takes the stroke as a parameter, so one glyph definition covers every weight it is drawn at.
 *
 * Round caps and joins on every path, matching Lucide's own `stroke-linecap` and `stroke-linejoin`.
 *
 * Remembered on the glyph and the width together: two nav states means two vectors per icon, and
 * rebuilding them on every recomposition would parse thirteen path strings a frame.
 */
@Composable
fun rememberLucideVector(
    glyph: LucideGlyph,
    strokeWidth: Float = LucideStroke.DEFAULT,
): ImageVector =
    remember(glyph, strokeWidth) {
        ImageVector
            .Builder(
                name = glyph.name,
                defaultWidth = LUCIDE_VIEWPORT.dp,
                defaultHeight = LUCIDE_VIEWPORT.dp,
                viewportWidth = LUCIDE_VIEWPORT,
                viewportHeight = LUCIDE_VIEWPORT,
            ).apply {
                glyph.pathData.forEach { data ->
                    addPath(
                        pathData = PathParser().parsePathString(data).toNodes(),
                        // Lucide is a stroked set: the shapes are outlines, never fills. Leaving the
                        // fill unset is what stops a rect or a circle rendering as a solid block.
                        fill = null,
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = strokeWidth,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    )
                }
            }.build()
    }

/**
 * A Lucide glyph, drawn.
 *
 * `stroke = SolidColor(Color.Black)` in the vector above is a placeholder that [Icon] recolours via
 * its tint, the same way Material's own icons work — the black never reaches the screen.
 *
 * @param contentDescription `null` for a glyph that repeats something already on screen, which is
 *   most of them: a stat tile's icon sits beside its own label.
 */
@Composable
fun LucideIcon(
    glyph: LucideGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    strokeWidth: Float = LucideStroke.DEFAULT,
) {
    Icon(
        imageVector = rememberLucideVector(glyph, strokeWidth),
        contentDescription = contentDescription,
        modifier = modifier,
        tint = tint,
    )
}

/** Lucide draws everything on a 24x24 grid. Every coordinate in [Lucide] assumes it. */
private const val LUCIDE_VIEWPORT = 24f

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun LucideNavStrokePreview() {
    HabitsTheme {
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            // The same five glyphs at both nav weights, which is the pair a static drawable could
            // not give us. Side by side is the only way to judge whether 2.2 against 1.5 reads.
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
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
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
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
        }
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun LucideAllGlyphsPreview() {
    HabitsTheme {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.chipGap)) {
            Lucide.all.forEach {
                LucideIcon(
                    glyph = it,
                    contentDescription = null,
                    modifier = Modifier.size(Sizing.iconMedium),
                    tint = HabitsTheme.colors.text.primary,
                )
            }
        }
    }
}

/** The five the bottom nav draws, and the only ones with two stroke weights. */
private val NavGlyphs =
    listOf(
        Lucide.LayoutGrid,
        Lucide.TextAlignJustify,
        Lucide.Calendar,
        Lucide.ChartNoAxesColumn,
        Lucide.Settings,
    )
