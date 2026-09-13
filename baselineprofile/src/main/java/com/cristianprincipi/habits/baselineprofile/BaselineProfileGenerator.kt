package com.cristianprincipi.habits.baselineprofile

import android.os.Build
import android.os.SystemClock
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Records the app's everyday path for the Baseline Profile — M14.9, ADR-143.
 *
 * Run it with `ANDROID_SERIAL=emulator-5554 ./gradlew :app:generateBaselineProfile`, never directly:
 * that task builds the `nonMinifiedRelease` variant, installs it on the emulator, and copies the result
 * into `app/src/release/generated/baselineProfiles/`, which is committed.
 *
 * **The journey is the one a user repeats, not the one they do once**: a cold start into Week, a
 * check-off, and a walk across the other tabs. The setup wizard is deliberately absent — see
 * `ProfileSeedActivity`, which is what makes a fresh install open on Week at all.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * ⚠⚠ Refuses a physical device before anything is seeded or tapped — the second of the three locks
     * in `baselineprofile/build.gradle.kts`. A generator run against the user's phone would seed
     * sample habits beside their real ones, and uninstall the app afterwards, taking their Notion
     * connection and widgets with it (`CLAUDE.md` §7).
     */
    @Before
    fun seed() {
        check(Build.HARDWARE == EMULATOR_HARDWARE) {
            "The Baseline Profile generator runs on an emulator only; this is ${Build.MANUFACTURER} ${Build.MODEL}."
        }
        device.executeShellCommand("am start -W -n $TARGET_PACKAGE/$TARGET_PACKAGE.profileseed.ProfileSeedActivity")
    }

    @Test
    fun everydayPath() =
        rule.collect(
            packageName = TARGET_PACKAGE,
            // The seeder is scaffolding, not a path the release APK has — it is not even in it.
            filterPredicate = { rule -> !rule.contains("profileseed", ignoreCase = true) },
        ) {
            killProcess()
            pressHome()
            startActivityAndWait()

            // A pill for today, whichever habit it belongs to. Its description is the habit's name
            // and the date in the device's own FULL style, exactly as `accessibleDate` writes it.
            val spokenDate = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault())
            val today = LocalDate.now().format(spokenDate)
            check(device.wait(Until.hasObject(By.descContains(today)), TIMEOUT_MS)) {
                "Week never showed a pill for $today — did the seed land, or is this the setup wizard?"
            }

            // ⚠⚠ Every step below proves it HAPPENED, and that is not decoration. The first run of this
            // generator passed on an emulator whose app window never drew: the accessibility tree
            // still answered every `findObject`, so the pill and all four tabs were "clicked", nothing
            // navigated, and the profile carried no rule for Month, Year or Stats. ADR-143 §6.
            // The first card's count is the tapped habit's, because the first pill for today is too.
            val countBefore = device.findObject(By.textContains(COUNT_MARK)).text
            device.findObject(By.descContains(today)).click()
            device.awaitUntil("the check-off never changed \"$countBefore\"") {
                findObject(By.textContains(COUNT_MARK))?.text != countBefore
            }

            TABS.forEach { (tab, title) ->
                device.findObject(By.text(tab)).click()
                // Where a title repeats its tab's label, the label alone is one match — so the screen is
                // open only once there are two.
                val needed = if (title == tab) 2 else 1
                device.awaitUntil("tapping $tab never opened its screen") { findObjects(By.text(title)).size >= needed }
            }
        }

    private fun UiDevice.awaitUntil(
        failure: String,
        condition: UiDevice.() -> Boolean,
    ) {
        val deadline = SystemClock.uptimeMillis() + TIMEOUT_MS
        while (!condition()) {
            check(SystemClock.uptimeMillis() < deadline) { failure }
            SystemClock.sleep(POLL_MS)
        }
    }

    private companion object {
        const val TARGET_PACKAGE = "com.cristianprincipi.habits"

        /** What every current Android emulator image reports. A phone reports its SoC. */
        const val EMULATOR_HARDWARE = "ranchu"
        const val TIMEOUT_MS = 10_000L
        const val POLL_MS = 100L

        /** Week's per-card count, "5 of 7" — `strings.xml`'s period count on a seven-day window. */
        const val COUNT_MARK = " of 7"

        /**
         * Each tab's label (`strings.xml`'s `tab_*`) and the title its screen shows, read off the running
         * app. Ends where it started, so the next iteration begins on Week.
         */
        val TABS = listOf("Month" to "Month", "Year" to "Year", "Stats" to "Statistics", "Week" to "Week")
    }
}
