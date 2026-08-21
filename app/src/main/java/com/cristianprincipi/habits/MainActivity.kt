package com.cristianprincipi.habits

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Bootstrap entry point.
 *
 * The theme is real as of M2.4. The body is still scaffolding: the navigation host replaces the
 * placeholder in M5.
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
                    BootstrapPlaceholder()
                }
            }
        }
    }
}

@Composable
private fun BootstrapPlaceholder(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(R.string.bootstrap_placeholder),
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Preview
@Composable
private fun BootstrapPlaceholderPreview() {
    HabitsTheme {
        BootstrapPlaceholder()
    }
}
