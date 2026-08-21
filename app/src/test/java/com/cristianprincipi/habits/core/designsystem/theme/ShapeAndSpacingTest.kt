package com.cristianprincipi.habits.core.designsystem.theme

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the radii and dimensions against `docs/tokens.md §3` and `§4`.
 *
 * The radii are the reason this file exists. `theme.css` remaps four of Tailwind's five corner
 * utilities and leaves the fifth alone, so the correct values are not the ones anybody would guess:
 * pills and inputs are 14dp rather than Tailwind's stock 12dp, and cards are 16dp. Both are asserted
 * against the wrong-but-plausible value as well as the right one.
 */
class ShapeAndSpacingTest {
    @Test
    fun `the radius ladder carries the values from tokens md`() {
        assertEquals(RoundedCornerShape(6.dp), HabitsShapes.extraSmall)
        assertEquals(RoundedCornerShape(8.dp), HabitsShapes.small)
        assertEquals(RoundedCornerShape(10.dp), HabitsShapes.medium)
        assertEquals(RoundedCornerShape(14.dp), HabitsShapes.large)
        assertEquals(RoundedCornerShape(16.dp), HabitsShapes.extraLarge)
    }

    @Test
    fun `pills and inputs are 14dp, not tailwind's stock 12dp`() {
        // `--radius: 10px` plus 4, not Tailwind's untouched `rounded-xl`. Getting this wrong would
        // be wrong on every interactive element in the app, and wrong by only 2dp — invisible in
        // review, visible side by side with the design.
        assertEquals(FOURTEEN, HabitsShapes.large.topStartPx())
        assertNotEquals(TWELVE, HabitsShapes.large.topStartPx())
    }

    @Test
    fun `cards keep tailwind's un-remapped 16dp`() {
        assertEquals(SIXTEEN, HabitsShapes.extraLarge.topStartPx())
    }

    @Test
    fun `the radius ladder ascends, because the material slots are size tiers not roles`() {
        val ladder =
            listOf(
                HabitsShapes.extraSmall,
                HabitsShapes.small,
                HabitsShapes.medium,
                HabitsShapes.large,
                HabitsShapes.extraLarge,
            ).map { it.topStartPx() }

        assertEquals(ladder.sorted(), ladder)
        assertEquals(ladder.size, ladder.distinct().size)
    }

    @Test
    fun `the dot radii are exposed as dp for canvas drawing`() {
        // Both dot grids are drawn with drawRoundRect, which takes a CornerRadius rather than a
        // Shape, so these have to survive as raw dp.
        assertEquals(3.dp, HabitsCorners.monthDot)
        assertEquals(1.dp, HabitsCorners.yearDot)
        assertEquals(RoundedCornerShape(3.dp), HabitsCorners.monthDotShape)
    }

    @Test
    fun `layout spacing carries the values from tokens md`() {
        assertEquals(16.dp, Spacing.screenPadding)
        assertEquals(20.dp, Spacing.headerHorizontal)
        assertEquals(112.dp, Spacing.bottomNavClearance)
    }

    @Test
    fun `the month card is the one card with tighter padding`() {
        assertEquals(14.dp, Spacing.cardPaddingCompact)
        assertEquals(16.dp, Spacing.cardPadding)
        assertNotEquals(Spacing.cardPadding, Spacing.cardPaddingCompact)
    }

    @Test
    fun `grid gaps carry the values from tokens md`() {
        assertEquals(2.dp, Spacing.yearDotGap)
        assertEquals(3.dp, Spacing.monthDotGap)
        assertEquals(6.dp, Spacing.weekPillGap)
        assertEquals(8.dp, Spacing.chipGap)
        assertEquals(12.dp, Spacing.cardGap)
    }

    @Test
    fun `component sizes carry the values from tokens md`() {
        assertEquals(4.dp, Sizing.navIndicatorDot)
        assertEquals(8.dp, Sizing.statusDot)
        assertEquals(28.dp, Sizing.statTileIcon)
        assertEquals(32.dp, Sizing.habitIconSmall)
        assertEquals(36.dp, Sizing.weekPillHeight)
        assertEquals(40.dp, Sizing.habitIconLarge)
    }

    @Test
    fun `the year dot has three sizes, smallest on the densest grid`() {
        assertTrue(Sizing.yearDotSmall < Sizing.yearDot)
        assertTrue(Sizing.yearDot < Sizing.yearDotLarge)
    }

    @Test
    fun `the three undersized controls stay below the touch minimum by design`() {
        // Not a bug to be fixed by growing them. The drawn size is the design; the touch target is
        // what grows around it (docs/tokens.md §4.4). This pins the fact that they differ, so a
        // future session cannot quietly "fix" the design by inflating the drawn size to 48dp.
        assertEquals(48.dp, Sizing.minimumTouchTarget)
        assertTrue(Sizing.navArrowButton < Sizing.minimumTouchTarget)
        assertTrue(Sizing.addButton < Sizing.minimumTouchTarget)
        assertTrue(Sizing.weekPillHeight < Sizing.minimumTouchTarget)
    }

    /**
     * The corner size in px at density 1, which for a dp-based corner is the dp value. Density has
     * to be supplied because `CornerSize` resolves lazily against the shape it is drawn into.
     */
    private fun CornerBasedShape.topStartPx() = topStart.toPx(Size(SHAPE_SIZE, SHAPE_SIZE), Density(1f))

    private companion object {
        const val SHAPE_SIZE = 1000f
        const val TWELVE = 12f
        const val FOURTEEN = 14f
        const val SIXTEEN = 16f
    }
}
