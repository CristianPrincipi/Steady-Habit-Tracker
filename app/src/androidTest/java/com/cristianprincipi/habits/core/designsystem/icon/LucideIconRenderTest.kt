package com.cristianprincipi.habits.core.designsystem.icon

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cristianprincipi.habits.core.designsystem.theme.HabitsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Proves every Lucide glyph parses and draws something sensible.
 *
 * Two failure modes matter here and neither is visible to the compiler.
 *
 * A path string `PathParser` cannot read throws when the vector is built, which the ink check below
 * catches by never getting there. The subtler one is the SVG-primitive conversion: `rect`, `circle`
 * and `line` elements were rewritten as path data, and a wrong arc sweep flag produces a shape that
 * parses perfectly and looks wrong. Ink coverage catches the gross version of that — a glyph that
 * drew nothing, or one that came out filled instead of stroked and blacked out its box.
 *
 * It does **not** catch a subtly wrong curve. That needs eyes, which is what M2.7 and M2.8 are for;
 * [writeGlyphSheetForInspection] exists to make looking cheap.
 */
@RunWith(AndroidJUnit4::class)
class LucideIconRenderTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun everyGlyphDrawsInkWithoutFillingItsBox() {
        // One setContent for the whole set, not one per glyph: the rule allows exactly one, and each
        // glyph gets its own tagged box so it can still be captured on its own.
        composeTestRule.setContent { HabitsTheme { GlyphColumn(PROBE_GLYPH_SIZE) } }

        Lucide.all.forEach { glyph ->
            val coverage = composeTestRule.onNodeWithTag(glyph.name).captureToImage().inkCoverage()

            // A stroked 24-unit glyph at stroke 2 inks a few percent of its box. Zero means it did
            // not draw; a large fraction means `fill` leaked in and the outline became a solid shape.
            assertTrue("${glyph.name} drew no ink (coverage $coverage)", coverage > MIN_COVERAGE)
            assertTrue(
                "${glyph.name} filled its box instead of stroking it (coverage $coverage)",
                coverage < MAX_COVERAGE,
            )
        }
    }

    @Test
    fun writeGlyphSheetForInspection() {
        // Not an assertion — this renders all thirteen at a size worth looking at and drops a PNG on
        // the device, so the arc conversions can be checked by eye with an `adb pull`. Kept as a
        // test because that is the only way to get a Compose frame onto a real device's filesystem.
        composeTestRule.setContent { HabitsTheme { GlyphColumn(SHEET_GLYPH_SIZE, tag = SHEET_TAG) } }

        val sheet = composeTestRule.onNodeWithTag(SHEET_TAG).captureToImage()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Internal storage, not external: on this API level scoped storage hides
        // /sdcard/Android/data from the shell user, so an external file cannot be pulled. The
        // app is debuggable, so `adb exec-out run-as <pkg> cat files/...` reaches this one.
        val file = File(context.filesDir, "lucide-glyph-sheet.png")
        file.outputStream().use { out ->
            sheet.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, out)
        }

        assertTrue("glyph sheet not written", file.length() > 0)
    }

    /** Every glyph in white on black, so "is this pixel ink" is a plain inequality. */
    @Composable
    private fun GlyphColumn(
        glyphSize: Dp,
        tag: String? = null,
    ) {
        Column(
            modifier =
                Modifier
                    .background(Color.Black)
                    .then(if (tag != null) Modifier.testTag(tag) else Modifier),
        ) {
            Lucide.all.chunked(GLYPHS_PER_ROW).forEach { row ->
                Row {
                    row.forEach { glyph ->
                        LucideIcon(
                            glyph = glyph,
                            contentDescription = null,
                            modifier = Modifier.size(glyphSize).testTag(glyph.name),
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }

    private fun ImageBitmap.inkCoverage(): Float {
        val pixels = toPixelMap()
        var inked = 0
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                if (pixels[x, y] != Color.Black) inked++
            }
        }
        return inked.toFloat() / (pixels.width * pixels.height)
    }

    private companion object {
        const val SHEET_TAG = "glyph-sheet"
        const val GLYPHS_PER_ROW = 5
        val PROBE_GLYPH_SIZE = 72.dp
        val SHEET_GLYPH_SIZE = 72.dp
        const val MIN_COVERAGE = 0.01f
        const val MAX_COVERAGE = 0.45f
        const val PNG_QUALITY = 100
    }
}
