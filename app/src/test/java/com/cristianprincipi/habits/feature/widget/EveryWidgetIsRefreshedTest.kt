package com.cristianprincipi.habits.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ⭐⭐ **M15.6d's finding, turned into something that fails if it happens again: a widget the
 * manifest declares but nothing wakes.**
 *
 * ### What it is guarding, and what it looked like when it was missing
 *
 * M15.6c built the wake-up half of the update path as `HabitWidgetRefresher`, whose whole body was
 * `HabitGridWidget().updateAll(context)`. M15.6d's plan then said the measure widget would inherit
 * that "for free". It would not have: a second widget class placed on a home screen would have been
 * refreshed by **nothing at all**, going stale until Glance's own idle timeout happened to restart
 * its session. ⚠ **Every gate in this project would have passed**, because until this file no test
 * named a widget class.
 *
 * ### ⚠ Why a test rather than the KDoc that already explains it
 *
 * ADR-082's rule, which `EveryDestinationIsOpaqueTest` follows for the same reason: **an inspection
 * is true on the day it is written and silent for ever afterwards.** `AppWidgetRefresher`'s KDoc
 * tells the next session that every widget belongs in that body. It cannot tell them that the third
 * widget they just added to the manifest is missing from it — and a widget that never updates is a
 * defect the user finds weeks later, on a home screen, with no error anywhere.
 *
 * ### ⚠ What it deliberately does not assert
 *
 * Not that `updateAll` is the right call, not the order, not that a refresh reaches a real widget —
 * that needs a device and a placed widget. The durable claim is only that **the set of widget
 * classes the manifest publishes and the set the refresher wakes are the same set.**
 */
class EveryWidgetIsRefreshedTest {
    @Test
    fun everyWidgetTheManifestPublishesIsWokenByTheRefresher() {
        val receivers = declaredWidgetReceivers()
        assertTrue(
            "Expected the manifest to publish at least the habit and measure widgets, found $receivers",
            receivers.size >= EXPECTED_WIDGETS,
        )

        val refresher = refresherCode()
        val unwoken = receivers.map { widgetClassOf(it) }.filterNot { refresher.contains("$it()") }

        assertEquals(
            "The manifest publishes a widget that AppWidgetRefresher never wakes, so it will go " +
                "stale on the user's home screen until Glance's idle timeout restarts its session. " +
                "Add it to refresh() (M15.6d).",
            emptyList<String>(),
            unwoken,
        )
    }

    @Test
    fun theRefresherWakesNothingItHasNoReceiverFor() {
        // The other direction, and it catches the likelier tidy-up: a widget deleted from the
        // manifest but left in the refresher is a class kept alive by one call nobody can reach.
        val published = declaredWidgetReceivers().map { widgetClassOf(it) }.toSet()
        val woken = Regex("""(\w+)\(\)\.updateAll\(""").findAll(refresherCode()).map { it.groupValues[1] }.toSet()

        assertEquals(published, woken)
    }

    /** The receiver class names the manifest registers as app-widget providers. */
    private fun declaredWidgetReceivers(): List<String> {
        val manifest = File(repoRoot(), MANIFEST_PATH)
        assertTrue("Expected the manifest at $manifest", manifest.isFile)
        return Regex("""<receiver[^>]*android:name="\.([\w.]+)"(.*?)</receiver>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(manifest.readText())
            .filter { it.groupValues[2].contains("android.appwidget.provider") }
            .map { it.groupValues[1].substringAfterLast('.') }
            .toList()
    }

    /**
     * The `GlanceAppWidget` a receiver hands over.
     *
     * Read from the receiver's own source rather than assumed from its name: `XReceiver` returning
     * `YWidget` is legal, and a test that guessed would pass while checking the wrong class.
     */
    private fun widgetClassOf(receiver: String): String {
        val source = File(repoRoot(), "$WIDGET_PACKAGE_PATH/$receiver.kt")
        assertTrue("Expected $receiver at $source", source.isFile)
        val match = Regex("""glanceAppWidget[^=]*=\s*(\w+)\(\)""").find(source.readText())
        return requireNotNull(match) { "No glanceAppWidget override found in $source" }.groupValues[1]
    }

    /** The refresher with its comments removed, so its KDoc may name a class without satisfying a test. */
    private fun refresherCode(): String {
        val file = File(repoRoot(), "$WIDGET_PACKAGE_PATH/AppWidgetRefresher.kt")
        assertTrue("Expected the refresher at $file", file.isFile)
        return file
            .readText()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//[^\n]*"""), "")
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
        const val WIDGET_PACKAGE_PATH = "app/src/main/java/com/cristianprincipi/habits/feature/widget"

        /** The two the app had when this was written — a floor, not an equality. */
        const val EXPECTED_WIDGETS = 2
    }
}
