package info.unterrainer.presserl.admin

import info.unterrainer.presserl.admin.api.CropRequest
import info.unterrainer.presserl.admin.api.EditMediaRequest
import info.unterrainer.presserl.admin.api.EllipseRequest
import info.unterrainer.presserl.admin.ui.media.Aspect
import info.unterrainer.presserl.admin.ui.media.Handle
import info.unterrainer.presserl.admin.ui.media.MAX_ELLIPSES
import info.unterrainer.presserl.admin.ui.media.MediaEditModel
import info.unterrainer.presserl.admin.ui.media.PixelEllipse
import info.unterrainer.presserl.admin.ui.media.PixelRect
import info.unterrainer.presserl.admin.ui.media.PreviewMapping
import info.unterrainer.presserl.admin.ui.media.blockSize
import info.unterrainer.presserl.admin.ui.media.hitRect
import info.unterrainer.presserl.admin.ui.media.pixelBlocks
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaEditModelTest {

    private val model = MediaEditModel(1600, 1067)

    private fun assertRatio(rect: PixelRect, ratio: Double) =
        assertTrue(abs(rect.width.toDouble() / rect.height - ratio) < 0.01, "$rect is not $ratio")

    private fun assertInside(rect: PixelRect) =
        assertTrue(rect.x >= 0 && rect.y >= 0 && rect.right <= 1600 && rect.bottom <= 1067, "$rect leaves the image")

    @Test
    fun freeCropFollowsTheHandleAndStaysInside() {
        val start = PixelRect(100, 50, 1200, 800)

        assertEquals(PixelRect(100, 50, 1300, 900), model.dragCrop(start, Handle.SE, 100, 100))
        assertEquals(PixelRect(100, 50, 1500, 1017), model.dragCrop(start, Handle.SE, 900, 900))
        assertEquals(PixelRect(0, 50, 1300, 800), model.dragCrop(start, Handle.W, -500, 0))
        assertEquals(PixelRect(1284, 50, 16, 800), model.dragCrop(start, Handle.W, 5000, 0))
        assertEquals(PixelRect(400, 267, 1200, 800), model.dragCrop(start, Handle.MOVE, 900, 900))
    }

    @Test
    fun cropKeepsThreeToTwoAtTheCorner() {
        model.chooseAspect(Aspect.RATIO_3_2)
        val start = model.state.crop!!
        assertRatio(start, 1.5)
        assertInside(start)

        for ((dx, dy) in listOf(-300 to 10, -50 to -400, 200 to 200)) {
            val rect = model.dragCrop(start, Handle.NW, dx, dy)
            assertRatio(rect, 1.5)
            assertInside(rect)
            assertEquals(start.right, rect.right)
            assertEquals(start.bottom, rect.bottom)
        }
        val edge = model.dragCrop(PixelRect(400, 300, 300, 200), Handle.E, 3000, 0)
        assertRatio(edge, 1.5)
        assertInside(edge)
    }

    @Test
    fun choosingAnAspectFitsTheCropAroundItsCentre() {
        model.update(model.state.copy(crop = PixelRect(0, 0, 1600, 1067)))

        model.chooseAspect(Aspect.RATIO_1_1)

        assertEquals(PixelRect(267, 0, 1067, 1067), model.state.crop)
    }

    @Test
    fun cropIsNeverSmallerThanSixteenPixels() {
        val drawn = model.drawCrop(500, 500, 502, 501)

        assertEquals(PixelRect(500, 500, 16, 16), drawn)
        model.chooseAspect(Aspect.RATIO_16_9)
        val tiny = model.drawCrop(500, 500, 501, 501)
        assertTrue(tiny.width >= 16 && tiny.height >= 16, tiny.toString())
    }

    @Test
    fun resetRemovesTheCrop() {
        model.chooseAspect(Aspect.RATIO_4_3)
        assertTrue(model.dirty)

        model.resetCrop()

        assertNull(model.state.crop)
        assertFalse(model.dirty)
    }

    @Test
    fun ellipsesAreAddedMovedResizedAndRemoved() {
        model.begin()
        model.addEllipse(model.drawEllipse(500, 300, 500, 300))
        model.setEllipse(0, model.drawEllipse(500, 300, 660, 520))
        model.end()
        assertEquals(listOf(PixelEllipse(580, 410, 80, 110)), model.state.ellipses)
        assertEquals(0, model.selected)

        model.setEllipse(0, model.moveEllipse(model.state.ellipses[0], 2000, -20))
        assertEquals(PixelEllipse(1599, 390, 80, 110), model.state.ellipses[0])
        model.setEllipse(0, model.resizeEllipse(model.state.ellipses[0], Handle.W, 1000, 0))
        assertEquals(4, model.state.ellipses[0].rx)

        model.removeSelected()
        assertTrue(model.state.ellipses.isEmpty())
        assertNull(model.selected)
    }

    @Test
    fun atMostFiftyEllipses() {
        repeat(MAX_ELLIPSES) { assertTrue(model.addEllipse(PixelEllipse(100, 100, 10, 10))) }

        assertFalse(model.addEllipse(PixelEllipse(100, 100, 10, 10)))
        assertEquals(MAX_ELLIPSES, model.state.ellipses.size)
    }

    @Test
    fun aGestureIsOneUndoStep() {
        model.begin()
        model.addEllipse(model.drawEllipse(100, 100, 100, 100))
        repeat(5) { model.setEllipse(0, model.drawEllipse(100, 100, 150 + it, 150 + it)) }
        model.end()
        model.chooseAspect(Aspect.RATIO_3_2)
        assertTrue(model.canUndo)

        model.undo()
        assertNull(model.state.crop)
        assertEquals(1, model.state.ellipses.size)
        model.undo()
        assertTrue(model.state.ellipses.isEmpty())
        assertFalse(model.dirty)
        assertFalse(model.canUndo)

        model.redo()
        model.redo()
        assertEquals(1, model.state.ellipses.size)
        assertRatio(model.state.crop!!, 1.5)
        assertFalse(model.canRedo)
    }

    @Test
    fun aNewChangeClearsRedo() {
        model.addEllipse(PixelEllipse(100, 100, 10, 10))
        model.undo()
        assertTrue(model.canRedo)

        model.addEllipse(PixelEllipse(200, 200, 10, 10))

        assertFalse(model.canRedo)
    }

    @Test
    fun requestIsInStoredPixelsAndLeavesOutAFullCrop() {
        model.addEllipse(PixelEllipse(600, 400, 80, 110))
        model.update(model.state.copy(crop = PixelRect(0, 0, 1600, 1067)))

        assertEquals(EditMediaRequest(4, null, listOf(EllipseRequest(600, 400, 80, 110))), model.request(4))

        model.update(model.state.copy(crop = PixelRect(100, 50, 1200, 800)))
        assertEquals(CropRequest(100, 50, 1200, 800), model.request(4).crop)
    }

    @Test
    fun previewMapsToStoredPixels() {
        // print rendition 3000 px shown 750 px wide, stored image 4096 px
        val mapping = PreviewMapping(4096, 2731, 750f, 500f)

        assertEquals(2048, mapping.toStoredX(375f))
        assertEquals(4096, mapping.toStoredX(900f))
        assertEquals(0, mapping.toStoredY(-5f))
        assertEquals(546, mapping.toStored(100f))
        assertEquals(375f, mapping.toView(2048), 0.01f)
    }

    @Test
    fun blocksFollowTheServerRule() {
        assertEquals(15, blockSize(PixelEllipse(400, 300, 100, 60)))
        assertEquals(12, blockSize(PixelEllipse(400, 300, 20, 20)))
        val blocks = pixelBlocks(PixelEllipse(400, 300, 100, 60), 1600, 1067)
        assertEquals(PixelRect(300, 240, 15, 15), blocks.first())
        assertEquals(14 * 8, blocks.size)
        val clipped = pixelBlocks(PixelEllipse(0, 0, 40, 40), 100, 100)
        assertEquals(PixelRect(0, 0, 8, 8), clipped.first())
    }

    @Test
    fun handlesAreHitCornersFirst() {
        val rect = PixelRect(100, 100, 400, 300)

        assertEquals(Handle.NW, hitRect(rect, 104, 97, 10))
        assertEquals(Handle.E, hitRect(rect, 505, 250, 10))
        assertEquals(Handle.MOVE, hitRect(rect, 300, 250, 10))
        assertNull(hitRect(rect, 50, 50, 10))
    }
}
