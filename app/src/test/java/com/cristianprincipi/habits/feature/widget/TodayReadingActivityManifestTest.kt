package com.cristianprincipi.habits.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The number widget's dialog owes how it behaves to four manifest attributes — M16.3. Each one is a
 * line someone could tidy away, and none of them shows on a debug run of the app itself:
 *
 * - without `taskAffinity=""` the dialog opens in the app's task, and closing it surfaces the app
 *   instead of returning to the home screen;
 * - without `excludeFromRecents` a finished dialog lingers in Recents as an empty card;
 * - without the translucent theme the home screen goes black behind the dialog;
 * - exported, any app on the phone could open a dialog that writes to the user's Notion.
 */
class TodayReadingActivityManifestTest {
    @Test
    fun theDialogsActivityKeepsItsOwnTaskStaysOutOfRecentsShowsTheHomeScreenAndIsPrivate() {
        val attributes = declaration()

        assertEquals("taskAffinity", "", attributes["taskAffinity"])
        assertEquals("excludeFromRecents", "true", attributes["excludeFromRecents"])
        assertEquals("theme", "@style/Theme.Steady.Translucent", attributes["theme"])
        assertEquals("exported", "false", attributes["exported"])
    }

    /** `android:` attributes of the activity's element, by local name. */
    private fun declaration(): Map<String, String> {
        val manifest = File(repoRoot(), MANIFEST_PATH)
        assertTrue("Expected the manifest at $manifest", manifest.isFile)
        val element =
            Regex("""<activity[^>]*android:name="\.feature\.widget\.TodayReadingActivity"[^>]*>""")
                .find(manifest.readText())
        val tag = requireNotNull(element) { "TodayReadingActivity is not declared in $manifest" }.value
        return Regex("""android:(\w+)="([^"]*)"""")
            .findAll(tag)
            .associate { match -> match.groupValues[1] to match.groupValues[2] }
    }

    /** Walks up from the module directory, so this works whichever of the two Gradle runs it from. */
    private fun repoRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null && !File(candidate, "settings.gradle.kts").isFile) {
            candidate = candidate.parentFile
        }
        return requireNotNull(candidate) { "No settings.gradle.kts above ${System.getProperty("user.dir")}" }
    }

    private companion object {
        const val MANIFEST_PATH = "app/src/main/AndroidManifest.xml"
    }
}
