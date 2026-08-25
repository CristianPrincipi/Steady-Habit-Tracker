package com.cristianprincipi.habits.core.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File

/**
 * Puts the app back to a fresh install before a test that launches an activity.
 *
 * ### Why a rule and not an `@Before`
 *
 * `createAndroidComposeRule` launches the activity from its own `before()`, and JUnit runs every
 * rule *outside* the `@Before` methods — so by the time an `@Before` ran, the activity would already
 * have composed against whatever was on disk. The precondition has to be established further out,
 * which means another rule, ordered ahead of it:
 *
 * ```
 * RuleChain.outerRule(FreshInstallRule()).around(composeTestRule)
 * ```
 *
 * ### What it deletes, and why the whole directory
 *
 * Both `PreferenceDataStoreFactory` stores live under `filesDir/datastore` — `settings` (which holds
 * the saved Notion property mapping) and `notion_auth` (which holds the encrypted token). The
 * directory goes rather than a list of file names, so that a store added later is covered without
 * anyone remembering to come back here.
 *
 * ### ⚠ What it does not do
 *
 * **It clears the disk, not a live DataStore.** DataStore caches in memory, and the app's stores are
 * Hilt `@Singleton`s that outlive an individual test within the instrumentation process — so this is
 * only decisive for the first activity launch of a run. That is enough for what it is for: the state
 * this guards against is a **human** having connected the app by hand in an earlier session, which
 * always arrives across a process restart. It would not undo a write made by an earlier test in the
 * same run, and no test in this suite makes one — `SettingsDataStoreRealFileTest` deliberately opens
 * its own file in `cacheDir` rather than the app's.
 *
 * **It leaves the Keystore key.** `NotionTokenStore.clear()` removes the key as well as the
 * ciphertext; this removes only the ciphertext. Harmless — a key with nothing to decrypt changes no
 * behaviour, and the next `save()` replaces it — but it is not the full disconnect, and a test about
 * disconnect should call the real thing rather than reach for this.
 */
class FreshInstallRule : TestRule {
    override fun apply(
        base: Statement,
        description: Description,
    ): Statement =
        object : Statement() {
            override fun evaluate() {
                val context = ApplicationProvider.getApplicationContext<Context>()
                File(context.filesDir, DATASTORE_DIRECTORY).deleteRecursively()
                base.evaluate()
            }
        }

    private companion object {
        /** Where `preferencesDataStoreFile` puts every store — see `PreferencesModule`/`AuthModule`. */
        const val DATASTORE_DIRECTORY = "datastore"
    }
}
