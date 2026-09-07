package com.cristianprincipi.habits.core.designsystem.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import com.cristianprincipi.habits.core.designsystem.theme.Spacing

/**
 * Swipe down to sync now — M15.3, and the design does not contain it.
 *
 * `CLAUDE.md` §9.10's logged default names *"manual pull-to-refresh"* beside the 15–30 minute
 * cadence, so this is decided scope that was never built rather than a new decision. The prototype
 * designs no gesture at all (`docs/design-spec.md` §8 lists no transitions), so §2's rule applies
 * and it takes the platform's convention: Material 3's own `PullToRefreshBox` and its own
 * indicator, rather than a spinner invented here.
 *
 * ### ⚠⚠ This is the one place sync is allowed to be visible, and the reason is the finger
 *
 * `CLAUDE.md` §3.5 is explicit that **sync is invisible until it fails**. That rule is about sync
 * the *app* starts: a background pass every 15 minutes has no business drawing a spinner over
 * someone's habits. This is the opposite case — the user reached for it — and an acknowledgement
 * they asked for is not the same thing as one the app volunteered.
 *
 * ⭐ **The user's ruling (2026-09-07) is that the indicator stays until the sync finishes**, over
 * the alternative of flashing and vanishing: *a pull that always looks instant tells you nothing
 * about whether it worked*. That also matches how the rest of the app reports — persistent state
 * rather than a toast.
 *
 * ⚠ **It adds no error surface.** A failure still lands where failures already land, so there is
 * exactly one place in the app that explains a broken sync. This only ever shows *running*.
 *
 * @param isRefreshing whether a sync is in flight — drives the indicator, and is the reason a pull
 *   during a background sync shows the indicator immediately rather than starting a second one.
 * @param onRefresh called on the gesture. Safe to call repeatedly: `SyncScheduler.syncNow()`
 *   de-duplicates on a unique name, so a second pull while one is running is a no-op.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PullToSyncBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    // ⚠⚠ **One state, hoisted, passed to both.** Calling `rememberPullToRefreshState()` twice —
    // once for the box, once for the indicator — compiles, runs, and produces a gesture with no
    // visible effect whatsoever: the box drives one object and the indicator renders another that
    // nothing ever moves. It cost a build-install-swipe cycle to see, because **Material 3 moves
    // only the indicator and leaves the content still**, so a dead indicator is an invisible
    // feature rather than a broken-looking one.
    val pullState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = pullState,
        indicator = {
            // ⚠ `align` is not optional here. The `indicator` slot is a `BoxScope`, and
            // `PullToRefreshBox`'s own default indicator aligns itself — a replacement that does not
            // lands wherever the Box's default alignment puts it, which is the **top-left corner**.
            // Seen on the emulator: a refresh arrow tucked against the left edge.
            //
            // The theme's own colours rather than the default surface pair, which is a lighter grey
            // than anything else in this app and reads as a piece of another design.
            PullToRefreshDefaults.Indicator(
                modifier = Modifier.align(Alignment.TopCenter).padding(top = Spacing.pullIndicatorInset),
                state = pullState,
                isRefreshing = isRefreshing,
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        content = { content() },
    )
}

@Preview(name = "Pull to sync — idle", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun PullToSyncIdlePreview() {
    HabitsTheme {
        PullToSyncBox(isRefreshing = false, onRefresh = {}) {
            Box(modifier = Modifier.fillMaxSize()) { Text("Week") }
        }
    }
}

@Preview(name = "Pull to sync — syncing", backgroundColor = 0xFF111113, showBackground = true, widthDp = 360)
@Composable
private fun PullToSyncRefreshingPreview() {
    HabitsTheme {
        PullToSyncBox(isRefreshing = true, onRefresh = {}) {
            Box(modifier = Modifier.fillMaxSize()) { Text("Week") }
        }
    }
}
