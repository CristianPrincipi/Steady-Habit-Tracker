package com.cristianprincipi.habits.core.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement
import java.io.File

/**
 * That [FreshInstallRule] has cleared the stores **by the time the test body runs**.
 *
 * The ordering is the whole point of the class and the one thing about it that can quietly stop
 * being true, so it is asserted from inside a statement the rule wraps rather than after the fact —
 * a check that ran afterwards would pass for a rule that cleared too late, which is precisely the
 * bug `MainActivityTest` had (an `@Before` runs after `createAndroidComposeRule` has launched the
 * activity).
 *
 * The rule is applied by hand instead of with `@get:Rule` because the file has to exist *before* it
 * runs, and there is no hook earlier than a rule to put one there.
 */
@RunWith(AndroidJUnit4::class)
class FreshInstallRuleTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun aStoreOnDiskIsGoneBeforeTheTestBodyStarts() {
        val directory = File(context.filesDir, "datastore")
        directory.mkdirs()
        // Not "settings" or "notion_auth": those name the real app's stores, and a file of nonsense
        // under either would be a plausible-looking corruption for someone to debug later.
        val probe = File(directory, "fresh_install_probe.preferences_pb")
        probe.writeText("left over from an earlier session")

        var existedInsideTheBody: Boolean? = null
        val rule =
            FreshInstallRule().apply(
                object : Statement() {
                    override fun evaluate() {
                        existedInsideTheBody = probe.exists()
                    }
                },
                Description.EMPTY,
            )

        rule.evaluate()

        assertEquals(false, existedInsideTheBody)
    }

    @Test
    fun anAppThatWasNeverConnectedIsNotAnError() {
        // The ordinary case: nothing on disk to delete. deleteRecursively() answers false for a
        // directory that is not there, and the rule must not care.
        File(context.filesDir, "datastore").deleteRecursively()

        var ran = false
        FreshInstallRule()
            .apply(
                object : Statement() {
                    override fun evaluate() {
                        ran = true
                    }
                },
                Description.EMPTY,
            ).evaluate()

        assertEquals(true, ran)
    }
}
