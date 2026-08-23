package com.cristianprincipi.habits

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cristianprincipi.habits.domain.repository.NotionSetupRepository
import com.cristianprincipi.habits.feature.notionsetup.NotionSetupScreen
import com.cristianprincipi.habits.feature.syncstatus.SyncStatusScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * ⚠ **Scaffolding. `HabitsNavHost` replaces all of it in M5.**
 *
 * There is no navigation in this app yet, and two screens now exist that a person has to be able to
 * reach: the connection wizard (M4.12) and sync status (M4.13). A screen nobody can open is a screen
 * nobody has looked at, and this project's rule is that the user's eye is the design authority
 * (`CLAUDE.md` §7).
 *
 * **The rule below is M12.9's, deliberately.** That step says: on first launch with no token, route
 * straight to the Notion setup screen; otherwise go to the app. This is the same decision made with
 * an `if` instead of a nav graph, so that when M5 and M12.9 arrive they are moving a rule that
 * already works rather than inventing one. The "otherwise" is Week once M6 exists; for now it is the
 * sync-status screen, which is the only other screen there is.
 *
 * **What this is not:** a navigation model. There is no back stack, no bottom nav and no way to
 * revisit setup once connected other than disconnecting. All of that is M5's, and none of it should
 * be built here.
 */
@Composable
fun HabitsAppRoot(
    modifier: Modifier = Modifier,
    viewModel: HabitsAppRootViewModel = hiltViewModel(),
) {
    val isConfigured by viewModel.isConfigured.collectAsStateWithLifecycle()

    if (isConfigured) {
        SyncStatusScreen(modifier = modifier)
    } else {
        NotionSetupScreen(modifier = modifier)
    }
}

/**
 * Whether setup has finished, and nothing else.
 *
 * ⚠ **Not "is a token stored".** The wizard saves the token at its *first* step, so routing on that
 * would swap the setup flow away the instant the user pressed Continue and they would never see the
 * database picker or the mapping. Found by looking at the running app, which is what `CLAUDE.md` §7
 * says screenshots are for.
 *
 * Starts `false`, so a cold start shows the wizard for the few milliseconds the DataStore read
 * takes. That is the right direction to be wrong in: an unconfigured app belongs there anyway.
 */
@HiltViewModel
class HabitsAppRootViewModel
    @Inject
    constructor(
        connection: NotionSetupRepository,
    ) : ViewModel() {
        val isConfigured: StateFlow<Boolean> =
            connection.isConfigured.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(SUBSCRIPTION_TIMEOUT_MILLIS),
                initialValue = false,
            )

        private companion object {
            const val SUBSCRIPTION_TIMEOUT_MILLIS = 5_000L
        }
    }
