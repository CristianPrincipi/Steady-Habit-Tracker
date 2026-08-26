package com.cristianprincipi.habits.feature.history

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.ui.StubScreen

/**
 * A year of completions as a dot grid — `docs/design-spec.md` §4.
 *
 * ⚠ **A stub. M8 builds this**, in the same milestone as [MonthScreen]. Both are read-only: at 3–5px
 * a dot cannot be tapped reliably, so check-off lives on the Week pills instead (`CLAUDE.md` §1).
 */
@Composable
fun YearScreen(modifier: Modifier = Modifier) {
    StubScreen(title = stringResource(R.string.screen_title_year), modifier = modifier)
}
