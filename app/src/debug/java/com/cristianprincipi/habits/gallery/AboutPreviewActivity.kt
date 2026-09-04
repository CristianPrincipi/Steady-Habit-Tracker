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
import androidx.compose.ui.Modifier
import com.cristianprincipi.habits.BuildConfig
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.feature.about.AboutContent

/**
 * The About screen, at the build's real version (M11.8).
 *
 * It exists for [SettingsPreviewActivity]'s reason and inherits its safety: About hangs off the
 * Settings hub, which is behind the setup gate, so an unconfigured build cannot reach it — and this
 * screen holds no state, touches no repository and can change nothing.
 *
 * ⭐ **Real `BuildConfig` values, unlike `AboutRenderTest`.** The test renders a version the build
 * does not have, precisely so a hardcoded string cannot pass it; this renders what will actually
 * ship, because the question a person asks of this screen is whether the real thing reads right.
 *
 * ```
 * adb shell am start -n com.cristianprincipi.habits/com.cristianprincipi.habits.gallery.AboutPreviewActivity
 * ```
 */
class AboutPreviewActivity : ComponentActivity() {
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
                    AboutContent(
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE,
                        // Its own `safeDrawing` insets, because there is no shell around it here
                        // (ADR-063's rule applies inside the graph; this is outside it).
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing),
                    )
                }
            }
        }
    }
}
