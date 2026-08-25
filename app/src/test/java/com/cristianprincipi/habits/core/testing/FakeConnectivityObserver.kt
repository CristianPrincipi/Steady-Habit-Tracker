package com.cristianprincipi.habits.core.testing

import com.cristianprincipi.habits.core.network.ConnectivityObserver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A [ConnectivityObserver] with no `ConnectivityManager` behind it.
 *
 * The real one needs a device, which is what `ConnectivityObserverTest` is for. Everything that
 * only cares *whether* the network is there tests against this instead — and going offline is a
 * one-line assignment here, where on an emulator it is an airplane-mode round trip.
 *
 * **Defaults to online**, because that is the uninteresting case: a test that says nothing about
 * the network should not accidentally be a test about being offline.
 */
class FakeConnectivityObserver(
    online: Boolean = true,
) : ConnectivityObserver {
    val online = MutableStateFlow(online)

    override val isOnline: Flow<Boolean> get() = online
}
