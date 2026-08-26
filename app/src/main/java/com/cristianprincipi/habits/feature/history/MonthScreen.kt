package com.cristianprincipi.habits.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.ui.StubScreen

/**
 * Monthly completion density for every habit at once — `docs/design-spec.md` §2.
 *
 * ⚠ **A stub. M8 builds this**, together with [YearScreen]: a two-column grid of habit cards, each
 * carrying a `MonthDotGrid`. The grid itself already exists in `core/designsystem/component` because
 * Stats renders the year one too (`docs/design-spec.md` §5.2).
 *
 * **The title will not stay "Month".** The design's header shows the month *name* — "August" — which
 * needs the clock abstraction rather than a constant, so it waits for the real screen.
 */
@Composable
fun MonthScreen(modifier: Modifier = Modifier) {
    StubScreen(title = stringResource(R.string.screen_title_month), modifier = modifier)
}
