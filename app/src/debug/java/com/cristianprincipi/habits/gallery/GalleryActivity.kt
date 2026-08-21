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
import androidx.compose.ui.Modifier
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme

/**
 * Every token and component in the design system, on one scrollable screen.
 *
 * **This exists to be looked at.** Claude can verify that a component renders and does not throw;
 * only a person can say whether it is right (ADR-019). `Plan.md` M2.8 is that conversation, and
 * this is what it is held over.
 *
 * Debug source set only, so it cannot reach a release build — see `src/debug/AndroidManifest.xml`.
 * No Hilt: the gallery has no dependencies beyond the design system itself, which is the point.
 */
class GalleryActivity : ComponentActivity() {
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
                    GalleryScreen()
                }
            }
        }
    }
}
