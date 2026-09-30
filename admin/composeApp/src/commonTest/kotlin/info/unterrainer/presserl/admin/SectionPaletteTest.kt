package info.unterrainer.presserl.admin

import androidx.compose.ui.graphics.Color
import info.unterrainer.presserl.admin.ui.section.SECTION_COLORS
import info.unterrainer.presserl.admin.ui.section.sectionColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** The section palette's light and dark variants. */
class SectionPaletteTest {

    @Test
    fun everyKeyHasDifferentLightAndDarkColours() {
        SECTION_COLORS.forEach { key ->
            assertNotEquals(sectionColor(key, dark = false), sectionColor(key, dark = true), key)
        }
    }

    @Test
    fun bothSchemesHaveEightDistinctColours() {
        listOf(false, true).forEach { dark ->
            val colours = SECTION_COLORS.map { sectionColor(it, dark) }
            assertEquals(8, colours.toSet().size, "dark = $dark")
            assertEquals(false, Color.Gray in colours, "dark = $dark")
        }
    }

    @Test
    fun unknownKeyFallsBackToGray() {
        assertEquals(Color.Gray, sectionColor("magenta", dark = false))
        assertEquals(Color.Gray, sectionColor("magenta", dark = true))
    }
}
