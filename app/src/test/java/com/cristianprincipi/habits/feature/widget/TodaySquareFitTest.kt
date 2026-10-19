package com.cristianprincipi.habits.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Where the corner square fits — M15.11. Each limit here was crossed once by a version that passed
 * every other gate, and showed only on the phone.
 */
class TodaySquareFitTest {
    @Test
    fun theTextKeepsRoomForAFiveLetterNameOnTheDefaultWidget() {
        // The square mirrors the badge, so it costs both text lines its own width plus the badge's
        // gap. A five-letter word at 15sp bold measured ~41-44dp on the phone. (A seven-letter one is
        // ~51dp: it fits on the user's 148dp-wide 2x2 widgets, and ellipsises on the declared 132dp.)
        val textRoom =
            textRoomDp(PREVIEW_SIZE.width.value, fitTodaySquare(PREVIEW_SIZE.width.value, PREVIEW_SIZE.height.value))

        assertTrue(
            "The header text has ${textRoom}dp beside the square; it needs at least $MIN_TEXT_ROOM",
            textRoom >= MIN_TEXT_ROOM,
        )
    }

    @Test
    fun aShortCardKeepsTheFirstSquare() {
        // `cf6cb32`'s square, where the dots are ~4dp: the picker's card, and a wide one-row-high card.
        assertEquals(SMALL_SQUARE, fitTodaySquare(PREVIEW_SIZE.width.value, PREVIEW_SIZE.height.value))
        assertEquals(SMALL_SQUARE, fitTodaySquare(286.5f, PREVIEW_SIZE.height.value))
    }

    @Test
    fun theUsers2x2DrawsTheSquareTheyPicked() {
        // The user picked the square their 2x2 widgets drew, for every card from 2x2 up. Read off the
        // phone, those are 148x148dp cards: ten columns of 8.67dp dots.
        val fit = fitTodaySquare(USER_2X2_DP, USER_2X2_DP)

        assertEquals(LARGE_SQUARE.sizeDp, fit.sizeDp, TOLERANCE)
        assertEquals(LARGE_SQUARE.endDp, fit.endDp, TOLERANCE)
        assertEquals(LARGE_SQUARE.strokeDp, fit.strokeDp, TOLERANCE)
    }

    @Test
    fun aBiggerCardBesideA2x2DrawsTheSameSquareOnTheScreen() {
        // ⚠⚠ ADR-158. The user asked for the 2x2's square on every bigger card, and in dp they got it:
        // 13dp on both. But their launcher draws the 286.5x179 card and the 148x148 one the same height,
        // each scaled to fit, so the 2x3's looked ~18% smaller beside the 2x2's. What the eye compares is
        // dp × (drawn height ÷ reported height), and the drawn heights are equal.
        val twoByTwo = fitTodaySquare(USER_2X2_DP, USER_2X2_DP)
        val twoByThree = fitTodaySquare(USER_2X3_WIDTH_DP, USER_2X3_HEIGHT_DP)

        assertEquals("size", onScreen(twoByTwo.sizeDp, USER_2X2_DP), onScreen(twoByThree.sizeDp), TOLERANCE)
        assertEquals("inset", onScreen(twoByTwo.endDp, USER_2X2_DP), onScreen(twoByThree.endDp), TOLERANCE)
        assertEquals("stroke", onScreen(twoByTwo.strokeDp, USER_2X2_DP), onScreen(twoByThree.strokeDp), TOLERANCE)
    }

    @Test
    fun everyBiggerCardDrawsASquareBiggerThanADotAndNoBiggerThanTheCapAllows() {
        // ⚠⚠ The first bug: on these the dots are 12dp and a fixed 10dp square was smaller than any of them.
        // ⚠ The first fix's flat 18dp was rejected on sight — on the phone it read larger than the 2x2's.
        // Sizes are the user's launcher's own `appWidgetSizes` (ADR-129); 286.5x179 is on their phone.
        BIG_CARDS.forEach { (width, height) ->
            val fit = fitTodaySquare(width, height)
            val dot = fitHabitWidgetGrid(width, height).dotDp

            assertTrue("${width}x$height: square ${fit.sizeDp} < dot $dot", fit.sizeDp > dot)
            assertTrue("${width}x$height: square ${fit.sizeDp}", fit.sizeDp <= MAX_SQUARE_DP + TOLERANCE)
        }
    }

    @Test
    fun theSquareNeverTakesTheNamesRoom() {
        // A narrow card can be tall, and a tall card has big dots — but the name comes first.
        sweep { width, height ->
            val fit = fitTodaySquare(width, height)
            val atSmall = textRoomDp(width, SMALL_SQUARE)
            val room = textRoomDp(width, fit)

            assertTrue(
                "${width}x$height: the square left the name ${room}dp, it had $atSmall",
                fit == SMALL_SQUARE || room >= MIN_NAME_ROOM_DP - TOLERANCE,
            )
        }
    }

    @Test
    fun theSquareOnlyGrowsAsTheCardDoes() {
        // A resize the other way must never be what makes it bigger — no jumps back and forth.
        sweep { width, height ->
            val fit = fitTodaySquare(width, height)
            assertTrue("${width}x$height taller", fitTodaySquare(width, height + STEP).sizeDp >= fit.sizeDp)
            assertTrue("${width}x$height wider", fitTodaySquare(width + STEP, height).sizeDp >= fit.sizeDp)
        }
    }

    @Test
    fun theSquareIsInsideItsOwnTouchTarget() {
        // The target is anchored on the card's corner and the square on the badge's centre line, so
        // moving either without the other can leave the square partly outside what a tap reaches.
        sweep { width, height ->
            val fit = fitTodaySquare(width, height)
            val squareLeft = fit.endDp + fit.sizeDp
            val squareBottom = CARD_PADDING.value + (BADGE_SIZE.value + fit.sizeDp) / 2

            assertTrue("${width}x$height: target too narrow", fit.targetWidthDp >= squareLeft)
            assertTrue("${width}x$height: target too short", SQUARE_TARGET_HEIGHT.value >= squareBottom)
        }
    }

    /** A size the launcher draws in a card it reports [reportedHeight] tall, as the eye sees it. */
    private fun onScreen(
        dp: Float,
        reportedHeight: Float = USER_2X3_HEIGHT_DP,
    ): Float = dp * DRAWN_HEIGHT_DP / reportedHeight

    /** Every size the widget can be resized to, and well past it. */
    private fun sweep(check: (width: Float, height: Float) -> Unit) {
        for (width in MIN_CARD_DP..MAX_CARD_DP step STEP.toInt()) {
            for (height in MIN_CARD_DP..MAX_CARD_DP step STEP.toInt()) check(width.toFloat(), height.toFloat())
        }
    }

    private companion object {
        const val MIN_TEXT_ROOM = 44f
        const val TOLERANCE = 0.01f

        /** The user's 2x2 habit widgets, measured off their phone on 2026-10-16. */
        const val USER_2X2_DP = 148f

        /** Their 2x3, as the launcher reports it. It and the 2x2s are drawn [DRAWN_HEIGHT_DP] tall. */
        const val USER_2X3_WIDTH_DP = 286.5f
        const val USER_2X3_HEIGHT_DP = 179f
        const val DRAWN_HEIGHT_DP = 110f

        /** The square at the grid's 12dp dot cap: one and a half dots. */
        const val MAX_SQUARE_DP = 18f

        val BIG_CARDS = listOf(286.5f to 179f, 184f to 295f, 295f to 295f, 405f to 295f)

        /** `habit_grid_widget_info.xml`: `minResizeWidth` 110dp and `minResizeHeight` 80dp. */
        const val MIN_CARD_DP = 80
        const val MAX_CARD_DP = 700
        const val STEP = 5f
    }
}
