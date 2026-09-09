package com.cristianprincipi.habits.gallery

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.navigation.HabitsShell
import com.cristianprincipi.habits.navigation.TopLevelRoute
import dagger.hilt.android.AndroidEntryPoint

/**
 * The five-tab shell, as if the app were already configured.
 *
 * ### Why this exists
 *
 * `HabitsShell` is only reached once a Notion mapping is stored, and a mapping cannot be put there
 * by a test or by hand: the app opens that DataStore on startup and DataStore allows one live
 * instance per file (ADR-032). So without this, the shell could be neither **seen** on a device nor
 * **driven** by an instrumented test until someone connected a real workspace — and the token that
 * would take can never live in a fixture (`CLAUDE.md` §3.6).
 *
 * Two other routes were tried first and both are dead ends worth not re-walking. A second
 * `PreferenceDataStoreFactory` on the app's `settings` file throws *"multiple DataStores active"* on
 * its first write, cancelling the scope afterwards notwithstanding. And a Hilt `@EntryPoint`
 * declared in `androidTest` is not implemented by the real application's component — `EntryPoints.get`
 * fails with a `ClassCastException`, because reaching the app's own graph that way needs
 * `HiltTestApplication` and a custom runner, which would replace the very application under test.
 *
 * ### Why an activity rather than a compose-only test
 *
 * Every destination in the graph builds its own ViewModel with `hiltViewModel()`, so the shell needs
 * a real `@AndroidEntryPoint` underneath it. `MainActivity` cannot serve: it calls `setContent` in
 * `onCreate`, and a compose test rule refuses to set content over that.
 *
 * ### Debug only, and not exported
 *
 * Debug source set only, exactly like [GalleryActivity], so it cannot reach a release build. Unlike
 * the gallery it carries no launcher entry and is **not exported**, because it hosts the real
 * `SyncStatusScreen` — which can disconnect a live Notion connection. A debug build is still
 * somebody's phone.
 *
 * ⚠ That does mean `adb shell am start` cannot open it: the shell uid is refused
 * (*"not exported from uid"*), and `run-as <pkg> am start` is refused too (*"package=com.android.shell
 * does not belong to uid"*). Instrumented tests can, because they run against this app. To look at
 * the shell by hand, flip `android:exported` to `true` in the debug manifest, install, screenshot,
 * and flip it back.
 */
@AndroidEntryPoint
class ShellHarnessActivity : ComponentActivity() {
    /**
     * The landing request `MainActivity` builds from a widget tap, exposed so a test can make one.
     *
     * ⚠ **The intent half is deliberately not reproduced here.** `MainActivity` turns an intent into
     * this value and clears it again; what has navigation logic in it — and what a widget tap is
     * actually asking for — is the shell's half, and that is what this harness can drive. The intent
     * flags themselves are the platform's behaviour, not ours, and a test of them would be a test of
     * Android.
     */
    var landing by mutableStateOf<TopLevelRoute?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        // Matching MainActivity rather than taking the default, for the reason in ADR-019: the
        // default follows the system light/dark setting and would render dark-on-dark system bars.
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
                    HabitsShell(
                        isConfigured = true,
                        landing = landing,
                        onLandingHandled = { landing = null },
                    )
                }
            }
        }
    }
}
