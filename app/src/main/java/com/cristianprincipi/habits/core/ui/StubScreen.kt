package com.cristianprincipi.habits.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * ⚠ **Scaffolding. Every use of this is deleted by the milestone that builds the real screen.**
 *
 * A destination that renders nothing is indistinguishable from a destination that is broken, so
 * M5.3's stubs say which screen they are and that they are empty on purpose. That is the whole job:
 * making the graph walkable and legible for M5.6, where the user clicks through it.
 *
 * ### The header here is not *the* header
 *
 * `docs/design-spec.md` §1 gives every view a header with a title, a `+` button on three of the five
 * views, and a sub-nav row on two. None of that is built here — a stub that grew the real header
 * would be M6 and M8's work smuggled into M5. This is a title and nothing else.
 *
 * ### ⭐ It takes no window insets, and that is the convention
 *
 * **The shell owns insets; a screen inside the graph takes the padding it is handed.** `HabitsShell`
 * puts every destination inside a `Scaffold`, whose content padding already covers the status bar
 * and the bottom bar, so a screen that also applied `safeDrawing` would be padded twice. That is not
 * hypothetical: `SyncStatusScreen` was written standalone in M4.13 and did exactly that once M5.2
 * put it in the graph, which is how the rule got written down. M5.5 is where this is verified on
 * every destination.
 */
@Composable
fun StubScreen(
    title: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = Spacing.headerHorizontal),
        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = HabitsTheme.colors.text.primary,
        )
        Text(
            text = stringResource(R.string.stub_placeholder),
            style = MaterialTheme.typography.bodyMedium,
            color = HabitsTheme.colors.text.secondary,
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360, heightDp = 240)
@Composable
private fun StubScreenPreview() {
    HabitsTheme {
        StubScreen(title = "Year")
    }
}
