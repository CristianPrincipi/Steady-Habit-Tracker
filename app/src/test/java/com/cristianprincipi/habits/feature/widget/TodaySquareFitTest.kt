package com.cristianprincipi.habits.feature.widget

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
        // ~51dp: it fits on the user's ~149dp widgets, and ellipsises on the declared 132dp.)
        val textRoom =
            PREVIEW_SIZE.width.value -
                CARD_PADDING.value * 2 -
                BADGE_SIZE.value -
                BADGE_GAP.value -
                SQUARE_TEXT_INSET.value

        assertTrue(
            "The header text has ${textRoom}dp beside the square; it needs at least $MIN_TEXT_ROOM",
            textRoom >= MIN_TEXT_ROOM,
        )
    }

    @Test
    fun theSquareIsInsideItsOwnTouchTarget() {
        // The target is anchored on the card's corner and the square on the badge's centre line, so
        // moving either without the other can leave the square partly outside what a tap reaches.
        val squareRight = CARD_PADDING.value + SQUARE_SIZE.value
        val squareBottom = CARD_PADDING.value + (BADGE_SIZE.value + SQUARE_SIZE.value) / 2

        assertTrue(
            "Target ${SQUARE_TARGET.width} is narrower than the square's reach",
            SQUARE_TARGET.width.value >= squareRight,
        )
        assertTrue(
            "Target ${SQUARE_TARGET.height} is shorter than the square's reach",
            SQUARE_TARGET.height.value >= squareBottom,
        )
    }

    private companion object {
        const val MIN_TEXT_ROOM = 44f
    }
}
