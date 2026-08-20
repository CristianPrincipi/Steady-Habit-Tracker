package com.cristianprincipi.habits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import dagger.hilt.android.AndroidEntryPoint

/**
 * Bootstrap entry point.
 *
 * The placeholder body is still scaffolding: `HabitsTheme` replaces `BootstrapTheme` in M2.4,
 * and the navigation host replaces the placeholder in M5.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super.onCreate, so the decor fitting is settled before any view exists.
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BootstrapTheme {
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

/** Placeholder for [com.cristianprincipi.habits.core.designsystem.theme] `HabitsTheme`, built in M2.4. */
@Composable
private fun BootstrapTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
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
    BootstrapTheme {
        BootstrapPlaceholder()
    }
}
