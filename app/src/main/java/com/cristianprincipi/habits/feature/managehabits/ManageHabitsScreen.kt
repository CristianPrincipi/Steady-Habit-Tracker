package com.cristianprincipi.habits.feature.managehabits

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.ui.StubScreen

/**
 * The habit list — **read-only**, by design.
 *
 * ⚠ **A stub, and an unreachable one**: nothing navigates here until the header's `+` exists (M6),
 * and M7.5 builds the screen itself. It is declared so the graph is complete.
 *
 * ADR-010 makes a habit a *column* in the user's Notion schema, so this screen links out to Notion
 * and offers app-local colour controls — it never creates, renames or deletes anything. The package
 * was called `habitedit` until M1.10; the name went with the capability.
 *
 * ⚠ **The title "Habits" is provisional.** This screen is not in the Figma prototype at all
 * (`docs/design-spec.md` §0), so M7.5 should put it to the user rather than inherit it from here.
 */
@Composable
fun ManageHabitsScreen(modifier: Modifier = Modifier) {
    StubScreen(title = stringResource(R.string.screen_title_habits), modifier = modifier)
}
