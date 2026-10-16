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
            textRoom(PREVIEW_SIZE.width.value, fitTodaySquare(PREVIEW_SIZE.width.value, PREVIEW_SIZE.height.value))

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
    fun everyBiggerCardDrawsTheSameSquareAndItIsBiggerThanADot() {
        // ⚠⚠ The bug: on these the dots are 12dp and a fixed 10dp square was smaller than any of them.
        // ⚠ And the first fix's 18dp was rejected on sight: bigger cards match the 2x2, no more.
        // Sizes are the user's launcher's own `appWidgetSizes` (ADR-129); 286.5x179 is on their phone.
        BIG_CARDS.forEach { (width, height) ->
            val fit = fitTodaySquare(width, height)
            val dot = fitHabitWidgetGrid(width, height).dotDp

            assertEquals("${width}x$height", LARGE_SQUARE, fit)
            assertTrue("${width}x$height: square ${fit.sizeDp} < dot $dot", fit.sizeDp > dot)
        }
    }

    @Test
    fun theSquareNeverTakesTheNamesRoom() {
        // A narrow card can be tall, and a tall card has big dots — but the name comes first.
        sweep { width, height ->
            val fit = fitTodaySquare(width, height)
            val atSmall = textRoom(width, SMALL_SQUARE)
            val room = textRoom(width, fit)

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

    /** What both header lines keep for text on a card [width] wide with [fit]'s square. */
    private fun textRoom(
        width: Float,
        fit: SquareFit,
    ): Float = width - CARD_PADDING.value * 2 - BADGE_SIZE.value - BADGE_GAP.value - fit.textEndInsetDp

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

        val BIG_CARDS = listOf(286.5f to 179f, 184f to 295f, 295f to 295f, 405f to 295f)

        /** `habit_grid_widget_info.xml`: `minResizeWidth` 110dp and `minResizeHeight` 80dp. */
        const val MIN_CARD_DP = 80
        const val MAX_CARD_DP = 700
        const val STEP = 5f
    }
}
