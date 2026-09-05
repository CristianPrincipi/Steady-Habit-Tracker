package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cristianprincipi.habits.core.designsystem.theme.Sizing

/**
 * Caps a screen that is **one column of reading** and centres it — M12.8.
 *
 * ### What it is for, and what it is not for
 *
 * Stats, Settings, About, sync status, the habit list and the wizard are all a single column of
 * cards, rows and prose. On a phone in portrait that column is the design; on a 923dp landscape
 * window it became a row of buttons a metre wide with one word floating in the middle, and a line
 * of body text nobody can track back to the start of.
 *
 * ⚠ **The three history tabs deliberately do not use this.** Week, Month and Year draw *habit
 * cards*, and the right answer for a card is another column of cards, not empty space — see
 * `Sizing.habitCardMinWidth`. A screen is one or the other; nothing in the app is both.
 *
 * ### ⭐ It is inert on every phone in portrait
 *
 * The cap is `Sizing.readingMaxWidth`, which sits just above the design's own content column, so it
 * cannot bite until the window is wider than about 472dp. That is the property worth protecting: a
 * change made for landscape must not quietly reshape the screens the design actually specifies, and
 * `AdaptiveWidthTest` fails if it starts to.
 *
 * ### The modifier order is load-bearing
 *
 * `fillMaxWidth` takes the whole window, `wrapContentWidth` measures the child against a minimum of
 * zero and centres what comes back, and `widthIn` is what stops the child taking everything offered.
 * Reordered, it either left-aligns or does nothing at all.
 */
fun Modifier.readingWidth(): Modifier =
    this
        .fillMaxWidth()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = Sizing.readingMaxWidth)
