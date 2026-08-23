package com.cristianprincipi.habits

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * Bootstrap entry point.
 *
 * ⚠ **The body is scaffolding, and M5 replaces it.** `HabitsNavHost` belongs here; until it exists
 * there is nowhere else to put a screen, and a setup flow nobody can reach is a setup flow nobody
 * has looked at. So M4.12's wizard is hosted directly, which also makes it verifiable on the
 * emulator (`CLAUDE.md` §7).
 *
 * **This is not where the connection flow lives in the finished app.** M12.9 is the real rule: on
 * first launch with no token, route to setup; otherwise go to Week. Wiring that needs navigation,
 * so it waits for M5.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Both bars are forced dark rather than left to `enableEdgeToEdge()`'s default, which
        // follows the SYSTEM light/dark setting. v1 is dark-only regardless of that setting
        // (ADR-009), so the default produced dark status-bar icons on our dark background —
        // effectively invisible — while the nav bar picked the light treatment. Revisit only if a
        // real light theme ever ships.
        // Called before super.onCreate, so decor fitting is settled before any view exists.
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
                    NotionSetupScreen()
                }
            }
        }
    }
}
