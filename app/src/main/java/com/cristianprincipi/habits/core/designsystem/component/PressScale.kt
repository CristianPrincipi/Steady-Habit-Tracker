package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale

/**
 * Shrinks a component while it is held down.
 *
 * The prototype's only interaction feedback is a CSS `active:scale-*` — `docs/tokens.md §6`. There
 * is no hover on Android and no ripple in the design, so this is what press feedback looks like
 * here. Pass the same [InteractionSource] the clickable uses, or the scale will never fire.
 *
 * Scale values live at the call sites rather than in the theme because each is quoted directly from
 * the element's own Tailwind class: 0.90 on pills and nav arrows, 0.95 on habit chips, 0.98 on the
 * Connect button.
 */
@Composable
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float,
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        label = "pressScale",
    )
    return this.scale(scale)
}
