package com.cristianprincipi.habits

import android.content.Context
import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.widget.WidgetRefresher
import com.cristianprincipi.habits.core.widget.refreshQuietly
import com.cristianprincipi.habits.navigation.HabitsNavHost
import com.cristianprincipi.habits.navigation.TopLevelRoute
import com.cristianprincipi.habits.navigation.Week
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

    /**
     * The tab something outside the composition has asked the app to land on, or `null`.
     *
     * Today there is exactly one producer — a tap on a home-screen widget, which asks for [Week] —
     * and [HabitsNavHost] is the one consumer. It is a piece of Compose state rather than an event
     * flow because of the **cold** case: a task restored after the process was killed composes with
     * its back stack already rebuilt on whichever tab the user left, and a `SharedFlow` emitted from
     * `onCreate` would be dropped for want of a collector. State survives that gap; an event does
     * not.
     *
     * The shell clears it through `onLandingHandled`, and that is not tidiness: without it a second
     * tap asking for the same tab would be the same value, so the effect keyed on it would not run
     * again — and the second tap is precisely the one made from another tab.
     */
    private var landing by mutableStateOf<TopLevelRoute?>(null)

    /**
     * Whether this launch's landing request has already been taken.
     *
     * ⚠⚠ **It is saved instance state, and a rotation is why.** `getIntent()` keeps returning the
     * intent that started the activity, so recreating for a configuration change re-reads a request
     * that was answered before the user turned the phone — and the app would jump back to Week from
     * whatever tab they had rotated on. Mutating the intent is not a guard: the system holds its own
     * copy and hands back a fresh one on recreation.
     */
    private var landingClaimed = false

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
        landingClaimed = savedInstanceState?.getBoolean(KEY_LANDING_CLAIMED) == true
        claimLanding(intent)
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
                    HabitsNavHost(
                        landing = landing,
                        onLandingHandled = { landing = null },
                    )
                }
            }
        }
    }

    /**
     * The warm path, and the one the whole mechanism exists for.
     *
     * A widget tap on an app that is merely in the background resumes the task exactly where it was
     * left — which for a user who was last on Stats is not "land on Week". [landOnWeek]'s
     * `CLEAR_TOP or SINGLE_TOP` is what routes that tap here instead of stacking a second activity
     * on top of the first.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A new intent is a new request, whatever the last one asked for.
        landingClaimed = false
        claimLanding(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_LANDING_CLAIMED, landingClaimed)
    }

    private fun claimLanding(intent: Intent) {
        if (landingClaimed || intent.action != ACTION_LAND_ON_WEEK) return
        landingClaimed = true
        landing = Week
    }

    companion object {
        /**
         * An intent that opens the app **on the Week tab**, wherever the app currently is.
         *
         * ⭐ **The user's request of 2026-09-09**, and it is what every home-screen widget does when
         * it is tapped: *"I would like to land on the Week page of the app (the primary page) … so I
         * can have a quick opening directly from the widgets."*
         *
         * ⚠⚠ **`FLAG_ACTIVITY_CLEAR_TASK` would be one line instead of all this, and it is the
         * wrong line.** It finishes the task and builds the activity again, which re-runs the setup
         * gate — and `HabitsNavHostViewModel.isConfigured` starts `false`, so a device slower than
         * this user's would draw the *"Connect to Notion"* wizard for as long as the DataStore read
         * takes. ADR-124 §6 records that as latent rather than fixed; a widget tap must not be the
         * thing that brings it back. `CLEAR_TOP or SINGLE_TOP` resumes the activity that already
         * exists and delivers this intent to [onNewIntent] instead.
         *
         * ⚠ The action rides on an **explicit** intent, so it needs no `intent-filter` — the
         * component decides where it goes and the action is only the message. It also keeps this
         * `PendingIntent` distinct from the launcher's, which is otherwise the same component with
         * no extras.
         */
        fun landOnWeek(context: Context): Intent =
            Intent(context, MainActivity::class.java).apply {
                action = ACTION_LAND_ON_WEEK
                flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }

        private const val ACTION_LAND_ON_WEEK = "com.cristianprincipi.habits.action.LAND_ON_WEEK"
        private const val KEY_LANDING_CLAIMED = "landing_claimed"
    }
}
