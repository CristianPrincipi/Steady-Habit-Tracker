package com.cristianprincipi.habits.feature.settings

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
import com.cristianprincipi.habits.core.designsystem.component.SecondaryButton
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * Settings — `docs/design-spec.md` §6.
 *
 * ⚠ **Barely a stub, and deliberately not empty.** M7 builds the real screen; until then this
 * carries the one thing that cannot wait: a way into sync status.
 *
 * ⭐ **Why that row exists.** M5.2 deleted `HabitsAppRoot`, which had been showing `SyncStatusScreen`
 * to every configured user — the only thing pointing at it. Without a row here the Notion
 * connection, the failure detail and **disconnect** would all be unreachable for a whole milestone,
 * on an app whose §3.6 promises the user can disconnect at any time. The user asked for it to keep a
 * way in (ADR-062); M7 absorbs this into the real screen.
 *
 * The prototype's own Settings hardcodes six Notion property names in a static schema table, which
 * `CLAUDE.md` §3.4 forbids. Keep its visual language; that table is replaced by the real mapping UI.
 */
@Composable
fun SettingsScreen(
    onOpenSyncStatus: () -> Unit,
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
            text = stringResource(R.string.screen_title_settings),
            style = MaterialTheme.typography.headlineSmall,
            color = HabitsTheme.colors.text.primary,
        )
        SecondaryButton(
            text = stringResource(R.string.settings_notion_sync),
            onClick = onOpenSyncStatus,
        )
    }
}

@Preview(backgroundColor = 0xFF111113, showBackground = true, widthDp = 360, heightDp = 240)
@Composable
private fun SettingsScreenPreview() {
    HabitsTheme {
        SettingsScreen(onOpenSyncStatus = {})
    }
}
