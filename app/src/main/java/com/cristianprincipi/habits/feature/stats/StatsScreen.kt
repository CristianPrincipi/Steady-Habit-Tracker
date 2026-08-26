package com.cristianprincipi.habits.feature.stats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.ui.StubScreen

/**
 * Completion rates, streaks and the year heatmap — `docs/design-spec.md` §5.
 *
 * ⚠ **A stub. M9 builds this.** Note the header title is "Statistics" while the tab reads "Stats":
 * that is the design's own asymmetry (§1), not a mistake to tidy up.
 */
@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    StubScreen(title = stringResource(R.string.screen_title_stats), modifier = modifier)
}
