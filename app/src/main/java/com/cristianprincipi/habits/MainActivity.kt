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
import com.cristianprincipi.habits.navigation.HabitsNavHost
import dagger.hilt.android.AndroidEntryPoint

/**
 * Bootstrap entry point.
 *
 * Everything above the theme is here: edge-to-edge, the dark system bars, and the one surface the
 * whole app draws on. The app itself is [HabitsNavHost], which decides between the setup wizard and
 * the five-tab shell.
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
                    HabitsNavHost()
                }
            }
        }
    }
}
