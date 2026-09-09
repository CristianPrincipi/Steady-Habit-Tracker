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
import androidx.lifecycle.lifecycleScope
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.widget.WidgetRefresher
import com.cristianprincipi.habits.core.widget.refreshQuietly
import com.cristianprincipi.habits.navigation.HabitsNavHost
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Bootstrap entry point.
 *
 * Everything above the theme is here: edge-to-edge, the dark system bars, and the one surface the
 * whole app draws on. The app itself is [HabitsNavHost], which decides between the setup wizard and
 * the five-tab shell.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    /**
     * ⚠⚠ **A placed widget can go stale for ever, and M15.6g is how we found out.**
     *
     * Neither widget declares `updatePeriodMillis` — deliberately, because M15.6c chose a push over
     * polling — so the only things that repaint one are a check-off, a sync that **pulled a day**
     * (`SyncOutcome.changedLocalData`), a resize, and a reboot. None of those is guaranteed when the
     * user installs a new build and looks at their home screen: the launcher keeps serving the
     * `RemoteViews` the *old* code produced, and a user whose habits have not changed today can
     * open the app, watch a sync succeed with nothing to pull, and see no difference at all.
     *
     * ⚠ **This was added on a false diagnosis and kept on a true one.** The report it was written
     * for — *"nothing changed"* — turned out to be an install that never happened (ADR-134 §11), not
     * a stale widget. The gap it closes is real all the same, and it is what makes "install and
     * look" a loop that cannot silently show yesterday's drawing.
     *
     * ⭐ **So opening the app repaints them, unconditionally.** It is the one moment the process is
     * certainly alive and the user is certainly looking, and it costs nothing for the majority who
     * have placed no widgets — `getGlanceIds` returns empty and no Glance session starts.
     *
     * ⚠ It is a repaint, not a sync: it draws what Room already holds, with whatever code is now
     * installed. That is the whole point — the defect it fixes is code reaching the phone without
     * reaching the home screen.
     */
    @Inject
    lateinit var widgets: WidgetRefresher

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
        // Only on a real creation: a rotation re-runs onCreate, and repainting every widget on
        // every rotation would be work nobody asked for. A cold start after an install is the case
        // this is here for, and that always arrives with a null bundle.
        if (savedInstanceState == null) {
            lifecycleScope.launch { widgets.refreshQuietly() }
        }
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
