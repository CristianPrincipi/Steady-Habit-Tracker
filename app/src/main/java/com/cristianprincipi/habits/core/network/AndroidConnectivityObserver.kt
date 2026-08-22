package com.cristianprincipi.habits.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ConnectivityObserver] over `ConnectivityManager`.
 *
 * Requires `ACCESS_NETWORK_STATE`, which is declared in the manifest for this class and nothing
 * else. It is a normal permission — granted at install, with no runtime prompt.
 *
 * **Validated, not merely connected.** A network that has an `INTERNET` capability but has not been
 * `VALIDATED` is the captive-portal case: the hotel wifi is attached, DNS resolves, and every call
 * to `api.notion.com` returns somebody's login page. Treating that as online would make the sync
 * engine burn retries against a router.
 */
@Singleton
class AndroidConnectivityObserver
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ConnectivityObserver {
        override val isOnline: Flow<Boolean> =
            callbackFlow {
                val manager = context.getSystemService(ConnectivityManager::class.java)
                if (manager == null) {
                    // No ConnectivityManager is not a crash. Report offline and stop: the app keeps
                    // working from Room, which is the whole point of CLAUDE.md §1.
                    trySendBlocking(false)
                    close()
                    return@callbackFlow
                }

                val callback =
                    object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            trySendBlocking(manager.hasValidatedInternet())
                        }

                        override fun onLost(network: Network) {
                            // Not simply `false`: another network may still be up, and on a phone
                            // leaving wifi for mobile data both callbacks fire in quick succession.
                            trySendBlocking(manager.hasValidatedInternet())
                        }

                        override fun onCapabilitiesChanged(
                            network: Network,
                            capabilities: NetworkCapabilities,
                        ) {
                            // Where the captive portal resolves: the network was already available,
                            // and only now gains NET_CAPABILITY_VALIDATED.
                            trySendBlocking(capabilities.hasValidatedInternet())
                        }
                    }

                // **This line is what makes an OFFLINE device report offline.** Probed on a real
                // emulator, 2026-08-22, both ways round (ADR-041):
                //   online  — registerDefaultNetworkCallback replays the current default network to
                //             a newly registered callback, so this emission is redundant.
                //   offline — there IS no default network, so NOTHING fires, and without this the
                //             flow emits nothing at all and every collector waits forever.
                // CLAUDE.md §1 treats offline as the normal case; an app that cannot tell it is
                // offline is worse than one that is.
                trySendBlocking(manager.hasValidatedInternet())
                manager.registerDefaultNetworkCallback(callback)

                awaitClose { manager.unregisterNetworkCallback(callback) }
            }.distinctUntilChanged()
                // The callbacks can arrive in bursts during a handover; only the latest matters.
                .conflate()
    }

private fun ConnectivityManager.hasValidatedInternet(): Boolean =
    activeNetwork
        ?.let(::getNetworkCapabilities)
        ?.hasValidatedInternet() == true

private fun NetworkCapabilities.hasValidatedInternet(): Boolean =
    hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
