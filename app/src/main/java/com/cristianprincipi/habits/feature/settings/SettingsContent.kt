package com.cristianprincipi.habits.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.R
import com.cristianprincipi.habits.core.datetime.Elapsed
import com.cristianprincipi.habits.core.designsystem.component.ConnectionStatusCard
import com.cristianprincipi.habits.core.designsystem.component.ConnectionSummary
import com.cristianprincipi.habits.core.designsystem.component.SecondaryButton
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * Everything the Settings screen draws — a status card and the ways out of it.
 *
 * ### ⭐ What this screen is, decided by the user on 2026-09-04 (M11.1)
 *
 * A **hub with a live status card**, not the prototype's one-screen connection form. The audit that
 * opened M11 found that every element `docs/design-spec.md` §6 designs was already built somewhere
 * else: the status card, retry and disconnect on `SyncStatusScreen`; the token and database-id
 * fields as the first two steps of the setup wizard; and the "Expected Database Schema" table
 * replaced by `EditMappingScreen`, because `CLAUDE.md` §3.4 forbids hardcoding a Notion property
 * name. So the question was never "build §6" — it was "what is Settings, now the connection has its
 * own screen".
 *
 * The answer keeps the card here so the tab **answers something without a tap** — connected? synced
 * when? — and keeps **disconnect one deliberate tap away** rather than on a tab a user can land on
 * by mis-aiming at the bottom bar. The two rows the screen had since M5.2 stay.
 *
 * ⭐ **The row order is reversed from M5.2's, and the card is why.** That version put "Your habits"
 * first, reasoning that it is the row a user opens to look at something where the other is the row
 * they open when something is wrong. With the connection card at the top, "Notion sync" is the
 * continuation of what the card just said, and putting the habit list between the two would break
 * a sentence in half.
 *
 * ⚠ **Undesigned below the card.** §6 designs no rows, so they are M2's [SecondaryButton]s — the
 * same control the habit list and the sync screen already use. `Plan.md` M2.8 applies: the user
 * judges this on a device, not from this file.
 */
@Composable
fun SettingsContent(
    state: SettingsUiState,
    onOpenSyncStatus: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        // No `windowInsetsPadding`: the shell owns insets and this screen takes the padding it is
        // handed (see `StubScreen`, and the same note on `SyncStatusContent`).
        modifier = modifier.fillMaxSize(),
    ) {
        SettingsTitle()
        SettingsSections(
            state = state,
            onOpenSyncStatus = onOpenSyncStatus,
            onOpenHabits = onOpenHabits,
            onOpenAbout = onOpenAbout,
        )
    }
}

/**
 * The title, padded exactly as Year's and Stats' are.
 *
 * ⚠⚠ **Horizontal padding only, and no top or bottom padding — and M11.1 got this wrong first.**
 * The screen was written with one `Column` carrying `padding(Spacing.screenPadding)` on every side,
 * which pushed the title 16dp below every other tab's. `HabitsShellInsetsTest`'s
 * `everyBareTitleStartsAtTheSameHeight` caught it with a **zero** tolerance and named both screens:
 * *"Settings starts at 184.0 but Year starts at 142.0"* — 42px, which is 16dp at this density. The gap below the title comes from the content column's own padding, exactly as
 * `StatsTitle` documents.
 */
@Composable
private fun SettingsTitle() {
    Text(
        text = stringResource(R.string.screen_title_settings),
        style = MaterialTheme.typography.headlineSmall,
        color = HabitsTheme.colors.text.primary,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.headerHorizontal),
    )
}

/**
 * The scrolling stack under the title.
 *
 * A plain `Column` with `verticalScroll` rather than a `LazyColumn`, for `StatsSections`' reason:
 * this screen has a card and two rows, and nothing here is a list whose length the user controls.
 * It scrolls at all because a large font scale can make three fixed items taller than a short phone.
 */
@Composable
private fun SettingsSections(
    state: SettingsUiState,
    onOpenSyncStatus: () -> Unit,
    onOpenHabits: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(Spacing.screenPadding),
        verticalArrangement = Arrangement.spacedBy(Spacing.settingsSectionGap),
    ) {
        ConnectionStatusCard(summary = state.connection)

        // The rows are one section, so they sit at the card gap rather than the section gap —
        // 20dp between two adjacent buttons reads as two unrelated things.
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.cardGap)) {
            SecondaryButton(
                text = stringResource(R.string.settings_notion_sync),
                onClick = onOpenSyncStatus,
            )
            SecondaryButton(
                text = stringResource(R.string.settings_habits),
                onClick = onOpenHabits,
            )
            // ⭐ Last, and it is the only row here a user never *needs*: the two above it are
            // where something gets fixed, and this one is where something gets read (M11.8).
            SecondaryButton(
                text = stringResource(R.string.settings_about),
                onClick = onOpenAbout,
            )
        }
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 400)
@Composable
private fun SettingsConnectedPreview() {
    HabitsTheme {
        SettingsContent(
            state =
                SettingsUiState(
                    connection = ConnectionSummary(isConnected = true, lastSynced = Elapsed.Minutes(3)),
                ),
            onOpenSyncStatus = {},
            onOpenHabits = {},
            onOpenAbout = {},
        )
    }
}

/**
 * ⚠ Reachable, despite the setup gate.
 *
 * `HabitsShell` routes on whether a **mapping** is stored, not on whether a token is — so a
 * disconnect that clears both leaves this screen drawn for the frame before the gate flips, and a
 * token revoked in Notion leaves it drawn indefinitely. The card has to read correctly in that
 * state rather than claiming a connection that is gone.
 */
@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 400, heightDp = 400)
@Composable
private fun SettingsDisconnectedPreview() {
    HabitsTheme {
        SettingsContent(
            state = SettingsUiState(),
            onOpenSyncStatus = {},
            onOpenHabits = {},
            onOpenAbout = {},
        )
    }
}
