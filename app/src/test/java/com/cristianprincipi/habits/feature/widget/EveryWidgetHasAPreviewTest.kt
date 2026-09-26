package com.cristianprincipi.habits.feature.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ⭐⭐ **`EveryWidgetIsRefreshedTest`'s argument, applied to the picker — M15.9.**
 *
 * ### ⚠⚠ Why this one is needed even more than its sibling
 *
 * `GlanceAppWidget.providePreview` **defaults to a no-op** — checked against the Glance 1.2.0 AAR,
 * where `providePreview$suspendImpl` simply returns `Unit`. So a widget that never overrides it does
 * not fail to build, does not warn, and does not throw: it previews **nothing**, silently, and the
 * picker quietly falls back to the app icon. That is not a hypothetical failure mode — it is exactly
 * how both of these widgets shipped in M15.6 and stayed for three months, until the user noticed the
 * icon in the picker was the one M14.1 had replaced.
 *
 * ⚠ The push has the same hole in the other direction: `AppWidgetPreviewPublisher` names its
 * receivers one by one, so a third widget is one forgotten line away from having a `providePreview`
 * that nothing ever calls.
 *
 * ### ⚠ What it deliberately does not assert
 *
 * Not that the preview looks right, not that the platform accepted it — that needs a device, API 35
 * and a human opening the picker. The durable claim is only that **the set of widget classes the
 * manifest publishes, the set that override `providePreview`, and the set the publisher pushes are
 * one set.**
 */
class EveryWidgetHasAPreviewTest {
    @Test
    fun everyWidgetTheManifestPublishesDrawsItsOwnPickerPreview() {
        val widgets = publishedWidgets()
        assertTrue("Expected the manifest to publish at least two widgets, found $widgets", widgets.size >= 2)

        val silent = widgets.filterNot { code(sourceOf(it)).contains("override suspend fun providePreview") }

        assertEquals(
            "A widget the manifest publishes never overrides providePreview. Glance's default is a " +
                "SILENT no-op, so its picker entry will fall back to the app icon and nothing will " +
                "say so (M15.9).",
            emptyList<String>(),
            silent,
        )
    }

    @Test
    fun everyPublishedWidgetIsAlsoPushedToTheSystem() {
        // A providePreview nobody calls is as invisible as no providePreview at all. The publisher
        // takes RECEIVER classes, not widget classes — see AppWidgetPreviewPublisher on why.
        val receivers = declaredWidgetReceivers()
        val publisher = code(File(repoRoot(), "$WIDGET_PACKAGE_PATH/AppWidgetPreviewPublisher.kt"))

        val unpushed = receivers.filterNot { publisher.contains("$it::class") }

        assertEquals(
            "The manifest publishes a widget that AppWidgetPreviewPublisher never pushes a preview " +
                "for. Add it to publish() (M15.9).",
            emptyList<String>(),
            unpushed,
        )
    }

    @Test
    fun everyPreviewIsComposedAtASizeAPreviewIsAllowedToHave() {
        // SizeMode.Exact is NOT a PreviewSizeMode — verified against the Glance 1.2.0 AAR — and both
        // widgets use Exact for their real size mode. A widget that left previewSizeMode alone would
        // take the library default rather than the footprint it actually lands at.
        val withoutOwnSize =
            publishedWidgets().filterNot { widget ->
                code(sourceOf(widget)).contains("override val previewSizeMode")
            }

        assertEquals(
            "A widget previews at the library's default size rather than PREVIEW_SIZE, so the picker " +
                "shows a different layout from the one the user will place (M15.9).",
            emptyList<String>(),
            withoutOwnSize,
        )
    }

    /** The `GlanceAppWidget` classes reachable from the manifest, via the receivers that publish them. */
    private fun publishedWidgets(): List<String> = declaredWidgetReceivers().map { widgetClassOf(it) }

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
     * Read from the receiver's own source rather than assumed from its name, as
     * `EveryWidgetIsRefreshedTest` does: `XReceiver` returning `YWidget` is legal, and a test that
     * guessed would pass while checking the wrong class.
     */
    private fun widgetClassOf(receiver: String): String {
        val match = Regex("""glanceAppWidget[^=]*=\s*(\w+)\(\)""").find(code(sourceOf(receiver)))
        return requireNotNull(match) { "No glanceAppWidget override found for $receiver" }.groupValues[1]
    }

    private fun sourceOf(className: String): File = File(repoRoot(), "$WIDGET_PACKAGE_PATH/$className.kt")

    /** A source file with its comments removed, so KDoc naming a thing cannot satisfy a test. */
    private fun code(file: File): String {
        assertTrue("Expected a source file at $file", file.isFile)
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
    }
}
