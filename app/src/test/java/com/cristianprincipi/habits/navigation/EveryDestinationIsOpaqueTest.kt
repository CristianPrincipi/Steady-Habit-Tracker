package com.cristianprincipi.habits.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ⭐⭐ **M15.1's deliverable: no destination in the shell can be transparent, and this fails if one
 * ever is again.**
 *
 * ### What it is guarding, and what it looked like when it was missing
 *
 * Every screen used to be drawn on nothing. `HabitsShell`'s `Scaffold` paints
 * `colorScheme.background` **once, behind the whole `NavHost`**, so the two entries that any
 * transition necessarily composes at the same time composited onto that one fill and onto each
 * other. A predictive-back gesture holds that overlap open for as long as the finger is down, and
 * the user saw it on their own phone: *"there is a delay that causes ghosting — I can see the
 * previous page for a second."* On the emulator, mid-swipe, Week's title, its date header, its
 * day-name row and its pills were all legible **through** Statistics' chips, tiles and chart, with
 * both bottom-bar items lit at once (ADR-122).
 *
 * ### ⚠ Why a test rather than the KDoc that already explains it
 *
 * ADR-082's rule, which this file follows deliberately: **an inspection is true on the day it is
 * written and silent for ever afterwards.** `screen`'s KDoc tells the next session why destinations
 * are opaque; it cannot tell them that the `composable<T>` they just typed out of habit — the name
 * every navigation-compose sample uses — has quietly reintroduced the defect. Nothing else would.
 * The whole point of routing all ten registrations through one function is that the rule cannot be
 * forgotten, and that is only true while `composable<T>` stays unreachable.
 *
 * ### ⚠ What it deliberately does not assert
 *
 * Not the colour, not the `Box`, not a pixel. The durable claim is only that **every destination is
 * registered through the one wrapper that paints a background**; how that wrapper paints it is free
 * to change. A screenshot assertion here would be a change detector aimed at Compose's internals,
 * on the instrument `CLAUDE.md` §7 and ADR-108 both warn about.
 */
class EveryDestinationIsOpaqueTest {
    @Test
    fun everyDestinationIsRegisteredThroughTheWrapperThatPaintsABackground() {
        val source = shellCode()

        val bare = Regex("""\bcomposable<""").findAll(source).count()
        val wrapped = Regex("""\bscreen<""").findAll(source).count()

        // One `composable<` is expected and required: it is inside `screen` itself, which is the
        // single place the graph is allowed to reach navigation-compose directly.
        assertEquals(
            "A destination is registered with composable<T> instead of screen<T>, so it draws on no " +
                "background of its own and will show the screen behind it during a back gesture " +
                "(ADR-122). Route it through screen<T>.",
            1,
            bare,
        )
        assertTrue(
            "Expected the shell to register its destinations through screen<T>; found $wrapped.",
            wrapped >= EXPECTED_DESTINATIONS,
        )
    }

    @Test
    fun theWrapperActuallyPaintsSomething() {
        // Without this the test above is satisfied by a `screen<T>` that wraps nothing, which is
        // exactly the rename a future session would reach for to make the first test pass.
        val source = shellCode()

        assertTrue(
            "screen<T> no longer applies a background, so routing every destination through it " +
                "proves nothing (ADR-122).",
            source.contains(".background(MaterialTheme.colorScheme.background)"),
        )
    }

    private fun shellSource(): String {
        val file = File(repoRoot(), SHELL_PATH)
        assertTrue("Expected the shell at $file", file.isFile)
        return file.readText()
    }

    /**
     * The file with its comments removed.
     *
     * ⚠ Not fussiness. `screen`'s own KDoc has to be able to say the words "`composable<T>`" while
     * explaining why nothing calls it — and a test that a doc comment can fail is a test the next
     * session learns to distrust rather than to read.
     */
    private fun shellCode(): String =
        shellSource()
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("""//[^\n]*"""), "")

    /** Walks up from the module directory, so this works whichever of the two Gradle runs it from. */
    private fun repoRoot(): File {
        var candidate: File? = File(System.getProperty("user.dir")).absoluteFile
        while (candidate != null && !File(candidate, "settings.gradle.kts").isFile) {
            candidate = candidate.parentFile
        }
        return requireNotNull(candidate) { "No settings.gradle.kts above ${System.getProperty("user.dir")}" }
    }

    private companion object {
        const val SHELL_PATH = "app/src/main/java/com/cristianprincipi/habits/navigation/HabitsNavHost.kt"

        /**
         * The ten destinations the graph had when this was written — a floor, not an equality.
         *
         * Adding an eleventh must not fail a test about opacity; registering one the wrong way must.
         */
        const val EXPECTED_DESTINATIONS = 10
    }
}
