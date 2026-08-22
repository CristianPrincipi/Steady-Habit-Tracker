package com.cristianprincipi.habits.core.network

import kotlinx.coroutines.flow.Flow

/**
 * Whether the device currently has usable internet.
 *
 * **Advisory, never a gate on the user.** `CLAUDE.md` §1: every action succeeds immediately and
 * locally, online or not, and "the network is treated as permanently unreliable". Nothing in the
 * check-off path may await this. What it is for is deciding when the sync engine is worth waking,
 * and telling the user why a sync has not run — not for disabling a button.
 *
 * An interface so that `core/network` above this line stays free of Android, and so the sync engine
 * can be tested offline without an emulator (`CLAUDE.md` §6).
 */
interface ConnectivityObserver {
    /**
     * Emits the current state **immediately**, then again on every change.
     *
     * The immediacy is the part that matters, and the case it protects is the **offline** one.
     * `registerDefaultNetworkCallback` replays the current default network to a new callback, so an
     * online device reports online either way — but an offline device has no default network, so
     * nothing fires, and a callback-only implementation emits nothing at all. Every collector then
     * waits forever for a value that never comes.
     *
     * Proved both ways on a real device (ADR-041). `CLAUDE.md` §1 treats offline as the normal case,
     * which makes "cannot tell it is offline" the worse of the two failures.
     */
    val isOnline: Flow<Boolean>
}
