package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.ui.fitScale
import kotlin.test.Test
import kotlin.test.assertEquals

class FitScaleTest {

    @Test
    fun contentThatFitsKeepsItsSize() {
        assertEquals(1f, fitScale(300, 390))
        assertEquals(1f, fitScale(390, 390))
    }

    @Test
    fun contentThatIsTooWideShrinksToTheAvailableWidth() {
        assertEquals(0.75f, fitScale(400, 300))
        assertEquals(360f, 480 * fitScale(480, 360))
    }

    @Test
    fun zeroOrNegativeWidthsLeaveTheScaleAtOne() {
        assertEquals(1f, fitScale(0, 390))
        assertEquals(1f, fitScale(-5, 390))
        assertEquals(1f, fitScale(400, 0))
        assertEquals(1f, fitScale(400, -1))
    }

    @Test
    fun unboundedWidthNeverShrinks() {
        assertEquals(1f, fitScale(5000, Int.MAX_VALUE))
    }
}
