package com.cristianprincipi.habits.feature.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Where the corner square fits — M15.11. Two limits squeeze it from opposite sides, and either one
 * moving is a defect only a phone would show.
 *
 * ⚠ **The first cut failed the first of these and passed every gate**: an 18dp square beside the
 * name left the name 38dp on the default widget, and most names ellipsised.
 */
class TodaySquareFitTest {
    @Test
    fun theNameKeepsRoomForASevenLetterWordOnTheDefaultWidget() {
        // The default footprint is PREVIEW_SIZE's width. A seven-letter word at 15sp bold measured
        // about 54dp in Noto Sans Bold, which is wider than Roboto, so 56dp is room with a margin.
        val nameRoom =
            PREVIEW_SIZE.width.value -
                CARD_PADDING.value * 2 -
                BADGE_SIZE.value -
                BADGE_GAP.value -
                SQUARE_NAME_INSET.value

        assertTrue(
            "The name line has ${nameRoom}dp beside the square; it needs at least $MIN_NAME_ROOM",
            nameRoom >= MIN_NAME_ROOM,
        )
    }

    @Test
    fun theSquareStaysClearOfTheCardsRoundedCorner() {
        // The card's corner is an arc of radius CARD_CORNER about (CARD_CORNER, CARD_CORNER). The
        // square's outer corner sits at (SQUARE_INSET, SQUARE_INSET); pushing it further out to win
        // the name more room puts it against the curve, which reads as clipped.
        val centre = CARD_CORNER.value
        val distance = hypot(centre - SQUARE_INSET.value, centre - SQUARE_INSET.value)

        assertTrue(
            "The square's corner is ${CARD_CORNER.value - distance}dp inside the card's curve; it needs $MIN_CORNER_CLEARANCE",
            CARD_CORNER.value - distance >= MIN_CORNER_CLEARANCE,
        )
    }

    private companion object {
        const val MIN_NAME_ROOM = 56f
        const val MIN_CORNER_CLEARANCE = 3f
    }
}
