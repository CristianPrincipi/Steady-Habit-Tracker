package com.cristianprincipi.habits.core.testing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` for a test dispatcher around each test.
 *
 * Without this, anything that touches `viewModelScope` throws on the JVM — there is no Android
 * main looper for `Dispatchers.Main` to dispatch to. Every ViewModel test in this project will
 * need it (`CLAUDE.md` §6: "Every ViewModel gets a test using a fake repository").
 *
 * Defaults to [UnconfinedTestDispatcher] so coroutines launched during `init` have already run by
 * the time the test body starts, which is what makes `StateFlow` assertions readable. Pass a
 * `StandardTestDispatcher` instead when a test needs to control ordering by hand.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
