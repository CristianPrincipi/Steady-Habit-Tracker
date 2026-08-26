package com.cristianprincipi.habits.feature.week

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.ui.StubScreen

/**
 * The landing tab, and the app's whole value proposition — `docs/design-spec.md` §3.
 *
 * ⚠ **A stub. M6 builds this**: a habit per row, seven pills each, today ringed, and a tap that
 * checks the day off. The pills keep their drawn 36dp height but **must** expand to a 48dp touch
 * target (`CLAUDE.md` §1, and the user called it out specifically).
 *
 * It is the start destination, so this is the first thing anyone sees — and until M6 it is the
 * emptiest screen in the app.
 */
@Composable
fun WeekScreen(modifier: Modifier = Modifier) {
    StubScreen(title = stringResource(R.string.screen_title_week), modifier = modifier)
}
