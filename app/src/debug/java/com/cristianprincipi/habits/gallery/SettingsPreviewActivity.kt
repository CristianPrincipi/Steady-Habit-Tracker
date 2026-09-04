package com.cristianprincipi.habits.gallery

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cristianprincipi.habits.core.datetime.Elapsed
import com.cristianprincipi.habits.core.designsystem.component.ConnectionSummary
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.feature.settings.SettingsContent
import com.cristianprincipi.habits.feature.settings.SettingsUiState

/**
 * The Settings hub with a made-up connection behind it (M11.1).
 *
 * ⚠ **Settings is the one tab that cannot be looked at any other way.** `HabitsShell` routes on
 * whether a property mapping is stored, so an unconfigured build shows the setup wizard and never
 * the five tabs; `ShellHarnessActivity` does render them, but it is `exported="false"` on purpose —
 * it hosts the real `SyncStatusScreen`, which can disconnect a live workspace. This activity holds
 * a `SettingsUiState` in memory, touches no repository and can disconnect nothing.
 *
 * ⭐ **What is worth judging here is the card, not the rows.** `docs/design-spec.md` §6 designs no
 * rows at all, so they are M2's `SecondaryButton`s; what M11.1 added is a status card that answers
 * *connected? synced when?* without a tap. Whether four states of one card earn the top of a tab is
 * a question for a person looking at a phone, which is `Plan.md` M2.8's whole point.
 *
 * ```
 * adb shell am start -n com.cristianprincipi.habits/com.cristianprincipi.habits.gallery.SettingsPreviewActivity
 * adb shell am start -S -n …/….gallery.SettingsPreviewActivity --es state never
 * adb shell am start -S -n …/….gallery.SettingsPreviewActivity --es state syncing
 * adb shell am start -S -n …/….gallery.SettingsPreviewActivity --es state waiting
 * adb shell am start -S -n …/….gallery.SettingsPreviewActivity --es state off
 * ```
 *
 * ⚠ `-S` matters when the activity is already up: `am start` compares intents with `filterEquals`,
 * which **ignores extras**, so without it a second launch resumes the old instance and the state
 * extra is silently dropped.
 */
class SettingsPreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Matching MainActivity rather than taking the default, for ADR-019's reason: the default
        // follows the system light/dark setting and would render dark-on-dark system bars.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            HabitsTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    SampleSettings(summaryFrom(intent.getStringExtra(EXTRA_STATE)))
                }
            }
        }
    }
}

private const val EXTRA_STATE = "state"

/**
 * The four things the card can say, one per extra.
 *
 * The default is the state a working app spends its life in — connected, synced a few minutes ago —
 * because a preview that opens on an error teaches you what the error looks like and nothing about
 * the screen.
 */
private fun summaryFrom(extra: String?): ConnectionSummary =
    when (extra) {
        "never" -> ConnectionSummary(isConnected = true, lastSynced = null)
        "syncing" -> ConnectionSummary(isConnected = true, isSyncing = true, lastSynced = Elapsed.Hours(2))
        "waiting" -> ConnectionSummary(isConnected = true, isWaitingForNetwork = true, lastSynced = Elapsed.Days(1))
        "off" -> ConnectionSummary(isConnected = false)
        else -> ConnectionSummary(isConnected = true, lastSynced = Elapsed.Minutes(3))
    }

/**
 * The real [SettingsContent] over a fixed summary.
 *
 * It takes its own `safeDrawing` insets because there is no shell around it here (ADR-063's rule
 * applies inside the graph; this is outside it). Both rows lead nowhere on purpose — real
 * navigation is the shell's, and it is the one thing this activity cannot show.
 */
@Composable
private fun SampleSettings(connection: ConnectionSummary) {
    SettingsContent(
        state = SettingsUiState(connection = connection),
        onOpenSyncStatus = {},
        onOpenHabits = {},
        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
    )
}
