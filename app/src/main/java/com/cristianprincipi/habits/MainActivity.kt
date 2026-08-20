package com.cristianprincipi.habits

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

/**
 * Bootstrap entry point.
 *
 * Everything here is scaffolding for M1.1 and is replaced later: Hilt's `@AndroidEntryPoint`
 * and `enableEdgeToEdge()` arrive in M1.9, `HabitsTheme` in M2.4, and the navigation host in M5.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
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
