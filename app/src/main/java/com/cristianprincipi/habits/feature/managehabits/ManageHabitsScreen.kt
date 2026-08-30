package com.cristianprincipi.habits.feature.managehabits

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * The habit list — **read-only**, by design.
 *
 * Stateful half only: it collects the state, forwards events, and owns the one thing a ViewModel
 * must not (see below). Everything drawn is in [ManageHabitsContent], which is the split
 * `CLAUDE.md` §6 asks for and the reason that file's previews can exist at all.
 *
 * ADR-010 makes a habit a *column* in the user's Notion schema, so this screen links out to Notion
 * and offers app-local colour controls — it never creates, renames or deletes anything. The package
 * was called `habitedit` until M1.10; the name went with the capability.
 *
 * ### ⭐ Reached from Settings, not from the header — the user's decision (M7.5)
 *
 * `HabitsNavHost` called this destination "unreachable until the header's `+` exists", and that `+`
 * must **never** exist: there is nothing this app can create. The way in is a Settings row instead,
 * chosen because this screen is reference plus one preference rather than a per-week action — and
 * because **M7.8's route back to the mapping links from the same place**.
 *
 * ### The URL is opened here, one layer above the ViewModel
 *
 * `LocalUriHandler` hands the link to the browser. That is an `Intent`, not a request:
 * `CLAUDE.md` §8 restricts the hosts *this app* contacts, and `api.notion.com` is still the only
 * one — opening `notion.so` is the user leaving. Doing it here rather than through an event keeps
 * `android.*` out of [ManageHabitsViewModel], which §6 requires.
 */
@Composable
fun ManageHabitsScreen(
    modifier: Modifier = Modifier,
    viewModel: ManageHabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    ManageHabitsContent(
        state = state,
        onEvent = viewModel::onEvent,
        // Nothing happens with no URL, and nothing needs to: the button that calls this is disabled
        // in exactly that case, so this null-check is the belt to that screen's braces.
        onOpenInNotion = {
            (state as? ManageHabitsUiState.Content)?.notionUrl?.let(uriHandler::openUri)
        },
        modifier = modifier,
    )
}
