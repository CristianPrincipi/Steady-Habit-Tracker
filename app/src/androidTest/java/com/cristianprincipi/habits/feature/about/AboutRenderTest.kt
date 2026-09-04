package com.cristianprincipi.habits.feature.about

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cristianprincipi.habits.R
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The About screen on a device (M11.8).
 *
 * ⚠ **This screen's job is to be true, so these tests check claims rather than layout.** The
 * version has to be the build's, the one host has to be named, the sentence about deletion has to
 * be there, and every licence the APK actually ships has to be attributed on screen.
 *
 * `createComposeRule` rather than the Android one: [AboutContent] takes the version as a parameter,
 * which is the whole reason `AboutScreen` reads `BuildConfig` and this does not.
 */
@RunWith(AndroidJUnit4::class)
class AboutRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun show(
        versionName: String = "0.1.0",
        versionCode: Int = 1,
    ) {
        composeTestRule.setContent {
            AboutContent(versionName = versionName, versionCode = versionCode)
        }
    }

    private fun string(
        id: Int,
        vararg args: Any,
    ) = context.getString(id, *args)

    /**
     * ⭐ Rendered at a version that is deliberately **not** the build's.
     *
     * A test that passed `0.1.0` would still pass if the screen ignored its parameter and hardcoded
     * today's version string — which is the one bug this screen cannot afford, because the number is
     * the only thing on it that changes.
     */
    @Test
    fun theVersionIsTheOneItIsHandedRatherThanAConstant() {
        show(versionName = "9.9.9", versionCode = 42)

        composeTestRule.onNodeWithText(string(R.string.about_version, "9.9.9", 42)).assertIsDisplayed()
    }

    /** `CLAUDE.md` §8: `api.notion.com` is the only host this app may ever contact. */
    @Test
    fun theOneHostItTalksToIsNamed() {
        show()

        composeTestRule.onNodeWithText(string(R.string.about_data_one_host)).assertIsDisplayed()
    }

    /**
     * The sentence a worried user is looking for, and the one ADR-036 makes true: v1 has no delete
     * at all, so disconnecting can only ever destroy the local mirror.
     */
    @Test
    fun itPromisesThatNothingInNotionIsDeleted() {
        show()

        composeTestRule.onNodeWithText(string(R.string.about_data_disconnect)).assertIsDisplayed()
    }

    /** The token is the app's one real secret (`CLAUDE.md` §3.6), so the screen says where it lives. */
    @Test
    fun itSaysWhereTheTokenIsKept() {
        show()

        composeTestRule.onNodeWithText(string(R.string.about_data_token)).assertIsDisplayed()
    }

    /**
     * ⭐⭐ **The drift guard, and the reason this test reads the APK's own assets.**
     *
     * The licences are attributed here as three fixed strings, and the texts they refer to are three
     * files in `assets/licences/`. Nothing but this test connects the two: bundle a fourth font
     * tomorrow, drop its licence in beside the others, and every other test in the suite still
     * passes while the app quietly ships an unattributed licence — which is the one thing the OFL
     * and the ISC actually forbid.
     *
     * It fails on the count rather than trying to match filenames to display names, because a
     * failure here should send you to `docs/attributions.md` and this screen, not to a naming
     * convention.
     */
    @Test
    fun everyLicenceTheApkShipsIsNamedOnScreen() {
        show()

        val shipped = context.assets.list("licences").orEmpty()

        assertEquals(
            "assets/licences holds ${shipped.size} files (${shipped.joinToString()}) but the " +
                "About screen names 3. Attribute the new one here and in docs/attributions.md.",
            3,
            shipped.size,
        )
        composeTestRule.onNodeWithText(string(R.string.about_licence_dm_sans)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.about_licence_dm_mono)).assertIsDisplayed()
        composeTestRule.onNodeWithText(string(R.string.about_licence_lucide)).assertIsDisplayed()
    }
}
