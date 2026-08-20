package com.cristianprincipi.habits

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Proves the Compose UI test harness can launch a Hilt-injected activity and find a node.
 *
 * The assertion is deliberately thin — the placeholder it looks for is replaced by the real
 * navigation host in M5. What matters here is that `createAndroidComposeRule` works against an
 * `@AndroidEntryPoint` activity at all, which is the thing later flow tests depend on
 * (`CLAUDE.md` §6: "Compose UI tests for the primary flows").
 *
 * Uses the **v2** rule: the v1 `createAndroidComposeRule` is deprecated in Compose BOM 2026.08.00.
 * v2 drives the test clock with a `StandardTestDispatcher` rather than an unconfined one, so work
 * is queued like ordinary coroutines instead of running eagerly. Starting new tests on v1 would
 * mean migrating every one of them later.
 */
@RunWith(AndroidJUnit4::class)
class MainActivityTest {
    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun bootstrapPlaceholderIsDisplayed() {
        val placeholder = composeTestRule.activity.getString(R.string.bootstrap_placeholder)

        composeTestRule.onNodeWithText(placeholder).assertIsDisplayed()
    }
}
