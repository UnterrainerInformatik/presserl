package info.unterrainer.presserl.admin.ui.media

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import info.unterrainer.presserl.admin.api.CropRequest
import info.unterrainer.presserl.admin.api.EditMediaRequest
import info.unterrainer.presserl.admin.api.EllipseRequest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Smallest crop side in stored pixels, as the server requires. */
const val MIN_CROP_SIDE = 16

/** Most ellipses per edit, as the server allows. */
const val MAX_ELLIPSES = 50

/** Smallest ellipse radius in stored pixels, as the server requires. */
const val MIN_RADIUS = 4

/** A rectangle in pixels of the stored image. */
data class PixelRect(val x: Int, val y: Int, val width: Int, val height: Int) {
    val right: Int get() = x + width
    val bottom: Int get() = y + height

    fun contains(px: Int, py: Int): Boolean = px in x..right && py in y..bottom
}

/** An ellipse in pixels of the stored image. */
data class PixelEllipse(val cx: Int, val cy: Int, val rx: Int, val ry: Int) {
    fun contains(px: Int, py: Int): Boolean {
        val dx = (px - cx).toDouble() / rx
        val dy = (py - cy).toDouble() / ry
        return dx * dx + dy * dy <= 1
    }

    /** The bounding box. */
    val bounds: PixelRect get() = PixelRect(cx - rx, cy - ry, 2 * rx, 2 * ry)
}

/** Crop aspect choices; [ratio] is width / height, `null` for free. */
enum class Aspect(val ratio: Double?) {
    FREE(null), RATIO_3_2(3.0 / 2), RATIO_4_3(4.0 / 3), RATIO_16_9(16.0 / 9), RATIO_1_1(1.0)
}

/** A grip of a rectangle: an edge, a corner, or the inside ([MOVE]). */
enum class Handle(val dx: Int, val dy: Int) {
    MOVE(0, 0), N(0, -1), S(0, 1), E(1, 0), W(-1, 0), NE(1, -1), NW(-1, -1), SE(1, 1), SW(-1, 1)
}

/** The undoable part of an edit: the crop (`null` for none) and the ellipses to pixelate, in stored pixels. */
data class EditState(val crop: PixelRect? = null, val ellipses: List<PixelEllipse> = emptyList())

/**
 * The edit of one stored image of [width] × [height] pixels: a crop and ellipses to pixelate, with undo and redo until
 * saved. Gestures call [begin], then [update] for every move, then [end]; a gesture counts as one undo step.
 */
class MediaEditModel(val width: Int, val height: Int) {
    var state by mutableStateOf(EditState())
        private set
    var aspect by mutableStateOf(Aspect.FREE)
        private set

    /** Index of the selected ellipse in [EditState.ellipses]; not undoable. */
    var selected by mutableStateOf<Int?>(null)
        private set
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val undoStack = ArrayDeque<EditState>()
    private val redoStack = ArrayDeque<EditState>()
    private var gestureStart: EditState? = null

    /** Whether there is anything to save. */
    val dirty: Boolean get() = state.crop != null || state.ellipses.isNotEmpty()

    val canAddEllipse: Boolean get() = state.ellipses.size < MAX_ELLIPSES

    /** Starts a gesture; [update]s until [end] form one undo step. */
    fun begin() {
        gestureStart = state
    }

    /** Shows [next] during a gesture (or as a single step outside one). */
    fun update(next: EditState) {
        if (gestureStart == null) change(next) else state = next
    }

    fun end() {
        val start = gestureStart ?: return
        gestureStart = null
        if (start != state) record(start)
    }

    private fun change(next: EditState) {
        if (next == state) return
        record(state)
        state = next
    }

    private fun record(previous: EditState) {
        undoStack.addLast(previous)
        redoStack.clear()
        updateFlags()
    }

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(state)
        state = previous
        keepSelectionValid()
        updateFlags()
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(state)
        state = next
        keepSelectionValid()
        updateFlags()
    }

    private fun updateFlags() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }

    private fun keepSelectionValid() {
        if (selected?.let { it >= state.ellipses.size } == true) selected = null
    }

    /** Chooses the crop aspect; an existing crop is fitted around its centre, none becomes the largest centred one. */
    fun chooseAspect(choice: Aspect) {
        aspect = choice
        val ratio = choice.ratio ?: return
        val crop = state.crop ?: PixelRect(0, 0, width, height)
        change(state.copy(crop = fitAspect(crop, ratio)))
    }

    /** Removes the crop. */
    fun resetCrop() = change(state.copy(crop = null))

    /** The crop while dragging [handle] of [start] by ([dx], [dy]) stored pixels (for [Handle.MOVE] a move). */
    fun dragCrop(start: PixelRect, handle: Handle, dx: Int, dy: Int): PixelRect =
        if (handle == Handle.MOVE) moveRect(start, dx, dy, width, height) else resizeRect(start, handle, dx, dy, aspect.ratio, width, height)

    /** A new crop drawn from ([x0], [y0]) to ([x1], [y1]), at least [MIN_CROP_SIDE] on a side. */
    fun drawCrop(x0: Int, y0: Int, x1: Int, y1: Int): PixelRect {
        val handle = when {
            x1 >= x0 && y1 >= y0 -> Handle.SE
            x1 < x0 && y1 >= y0 -> Handle.SW
            x1 >= x0 -> Handle.NE
            else -> Handle.NW
        }
        val start = PixelRect(x0.coerceIn(0, width), y0.coerceIn(0, height), 0, 0)
        return resizeRect(start, handle, x1 - x0, y1 - y0, aspect.ratio, width, height)
    }

    fun select(index: Int?) {
        selected = index?.takeIf { it in state.ellipses.indices }
    }

    /** The ellipse drawn from ([x0], [y0]) to ([x1], [y1]): that box, radii at least [MIN_RADIUS], centre inside. */
    fun drawEllipse(x0: Int, y0: Int, x1: Int, y1: Int): PixelEllipse = PixelEllipse(
        ((x0 + x1) / 2).coerceIn(0, width - 1),
        ((y0 + y1) / 2).coerceIn(0, height - 1),
        max(MIN_RADIUS, abs(x1 - x0) / 2),
        max(MIN_RADIUS, abs(y1 - y0) / 2),
    )

    /** Adds [ellipse] (selected afterwards); `false` when [MAX_ELLIPSES] are there already. */
    fun addEllipse(ellipse: PixelEllipse): Boolean {
        if (!canAddEllipse) return false
        update(state.copy(ellipses = state.ellipses + ellipse))
        selected = state.ellipses.lastIndex
        return true
    }

    /** Replaces the ellipse at [index]. */
    fun setEllipse(index: Int, ellipse: PixelEllipse) {
        if (index !in state.ellipses.indices) return
        update(state.copy(ellipses = state.ellipses.toMutableList().also { it[index] = ellipse }))
    }

    /** [start] moved by ([dx], [dy]) with its centre kept inside the image. */
    fun moveEllipse(start: PixelEllipse, dx: Int, dy: Int): PixelEllipse =
        start.copy(cx = (start.cx + dx).coerceIn(0, width - 1), cy = (start.cy + dy).coerceIn(0, height - 1))

    /** [start] resized by dragging [handle] of its bounding box; the opposite side stays, radii at least [MIN_RADIUS]. */
    fun resizeEllipse(start: PixelEllipse, handle: Handle, dx: Int, dy: Int): PixelEllipse {
        if (handle == Handle.MOVE) return moveEllipse(start, dx, dy)
        var left = start.cx - start.rx
        var right = start.cx + start.rx
        var top = start.cy - start.ry
        var bottom = start.cy + start.ry
        if (handle.dx < 0) left = min(left + dx, right - 2 * MIN_RADIUS)
        if (handle.dx > 0) right = max(right + dx, left + 2 * MIN_RADIUS)
        if (handle.dy < 0) top = min(top + dy, bottom - 2 * MIN_RADIUS)
        if (handle.dy > 0) bottom = max(bottom + dy, top + 2 * MIN_RADIUS)
        return drawEllipse(left, top, right, bottom)
    }

    /** Removes the selected ellipse. */
    fun removeSelected() {
        val index = selected ?: return
        selected = null
        change(state.copy(ellipses = state.ellipses.filterIndexed { i, _ -> i != index }))
    }

    /** The request for the server; a crop covering the whole image is left out. */
    fun request(version: Long): EditMediaRequest = EditMediaRequest(
        version,
        state.crop?.takeUnless { it == PixelRect(0, 0, width, height) }?.let { CropRequest(it.x, it.y, it.width, it.height) },
        state.ellipses.map { EllipseRequest(it.cx, it.cy, it.rx, it.ry) },
    )
}

/** [rect] moved by ([dx], [dy]), kept inside an image of [width] × [height]. */
fun moveRect(rect: PixelRect, dx: Int, dy: Int, width: Int, height: Int): PixelRect =
    rect.copy(x = (rect.x + dx).coerceIn(0, max(0, width - rect.width)), y = (rect.y + dy).coerceIn(0, max(0, height - rect.height)))

/** The largest rectangle of [ratio] (width / height) inside [rect], around its centre, at least [MIN_CROP_SIDE] a side. */
fun fitAspect(rect: PixelRect, ratio: Double): PixelRect {
    var w = rect.width.toDouble()
    var h = rect.height.toDouble()
    if (w / h > ratio) w = h * ratio else h = w / ratio
    w = max(w, MIN_CROP_SIDE * max(1.0, ratio))
    h = w / ratio
    val x = rect.x + (rect.width - w) / 2
    val y = rect.y + (rect.height - h) / 2
    return PixelRect(x.roundToInt(), y.roundToInt(), w.roundToInt(), h.roundToInt())
}

/**
 * [start] with [handle] dragged by ([dx], [dy]) inside an image of [width] × [height], at least [MIN_CROP_SIDE] a side.
 * With a [ratio] the opposite corner (or, for an edge, the opposite edge's middle) stays and the ratio is kept.
 */
fun resizeRect(start: PixelRect, handle: Handle, dx: Int, dy: Int, ratio: Double?, width: Int, height: Int): PixelRect {
    if (ratio == null) {
        var left = start.x
        var top = start.y
        var right = start.right
        var bottom = start.bottom
        if (handle.dx < 0) left = (left + dx).coerceIn(0, max(0, right - MIN_CROP_SIDE))
        if (handle.dx > 0) right = (right + dx).coerceIn(min(width, left + MIN_CROP_SIDE), width)
        if (handle.dy < 0) top = (top + dy).coerceIn(0, max(0, bottom - MIN_CROP_SIDE))
        if (handle.dy > 0) bottom = (bottom + dy).coerceIn(min(height, top + MIN_CROP_SIDE), height)
        return PixelRect(left, top, right - left, bottom - top)
    }
    val anchorX = when {
        handle.dx > 0 -> start.x.toDouble()
        handle.dx < 0 -> start.right.toDouble()
        else -> start.x + start.width / 2.0
    }
    val anchorY = when {
        handle.dy > 0 -> start.y.toDouble()
        handle.dy < 0 -> start.bottom.toDouble()
        else -> start.y + start.height / 2.0
    }
    val draggedW = (start.width + handle.dx * dx).toDouble()
    val draggedH = (start.height + handle.dy * dy).toDouble()
    var w = when {
        handle.dx != 0 && handle.dy != 0 -> max(draggedW, draggedH * ratio)
        handle.dx != 0 -> draggedW
        else -> draggedH * ratio
    }
    val roomX = when {
        handle.dx > 0 -> width - anchorX
        handle.dx < 0 -> anchorX
        else -> 2 * min(anchorX, width - anchorX)
    }
    val roomY = when {
        handle.dy > 0 -> height - anchorY
        handle.dy < 0 -> anchorY
        else -> 2 * min(anchorY, height - anchorY)
    }
    val maxW = min(roomX, roomY * ratio)
    val minW = min(maxW, MIN_CROP_SIDE * max(1.0, ratio))
    w = w.coerceIn(minW, maxW)
    val h = w / ratio
    val left = when {
        handle.dx > 0 -> anchorX
        handle.dx < 0 -> anchorX - w
        else -> anchorX - w / 2
    }
    val top = when {
        handle.dy > 0 -> anchorY
        handle.dy < 0 -> anchorY - h
        else -> anchorY - h / 2
    }
    val x = left.roundToInt().coerceIn(0, width)
    val y = top.roundToInt().coerceIn(0, height)
    return PixelRect(x, y, min(w.roundToInt(), width - x), min(h.roundToInt(), height - y))
}

/** Side of the pixelation blocks of [ellipse], the server's rule: the larger of 12 and an eighth of the smaller diameter. */
fun blockSize(ellipse: PixelEllipse): Int = max(12, min(2 * ellipse.rx, 2 * ellipse.ry) / 8)

/**
 * The pixelation blocks of [ellipse] in an image of [width] × [height]: they tile its bounding box from the top-left
 * corner, clipped to the image, as the server applies them.
 */
fun pixelBlocks(ellipse: PixelEllipse, width: Int, height: Int): List<PixelRect> {
    val b = blockSize(ellipse)
    val blocks = mutableListOf<PixelRect>()
    var by = ellipse.cy - ellipse.ry
    while (by < ellipse.cy + ellipse.ry) {
        var bx = ellipse.cx - ellipse.rx
        while (bx < ellipse.cx + ellipse.rx) {
            val x0 = max(0, bx)
            val y0 = max(0, by)
            val x1 = min(width, bx + b)
            val y1 = min(height, by + b)
            if (x0 < x1 && y0 < y1) blocks += PixelRect(x0, y0, x1 - x0, y1 - y0)
            bx += b
        }
        by += b
    }
    return blocks
}

/**
 * Maps between the preview on screen (the stored image scaled to [viewWidth] × [viewHeight] pixels) and pixels of
 * the stored image of [storedWidth] × [storedHeight].
 */
class PreviewMapping(val storedWidth: Int, val storedHeight: Int, val viewWidth: Float, val viewHeight: Float) {
    private val scale: Float = if (viewWidth > 0) storedWidth / viewWidth else 1f

    fun toStoredX(x: Float): Int = (x * scale).roundToInt().coerceIn(0, storedWidth)
    fun toStoredY(y: Float): Int = (y * scale).roundToInt().coerceIn(0, storedHeight)

    /** A distance on screen in stored pixels, unclamped. */
    fun toStored(distance: Float): Int = (distance * scale).roundToInt()

    fun toView(stored: Int): Float = stored / scale
}

/**
 * The grip of [rect] at ([x], [y]) within [tolerance] stored pixels: a corner before an edge, the inside as
 * [Handle.MOVE]; `null` outside.
 */
fun hitRect(rect: PixelRect, x: Int, y: Int, tolerance: Int): Handle? {
    val nearLeft = abs(x - rect.x) <= tolerance
    val nearRight = abs(x - rect.right) <= tolerance
    val nearTop = abs(y - rect.y) <= tolerance
    val nearBottom = abs(y - rect.bottom) <= tolerance
    val withinX = x in rect.x - tolerance..rect.right + tolerance
    val withinY = y in rect.y - tolerance..rect.bottom + tolerance
    return when {
        nearLeft && nearTop -> Handle.NW
        nearRight && nearTop -> Handle.NE
        nearLeft && nearBottom -> Handle.SW
        nearRight && nearBottom -> Handle.SE
        nearTop && withinX -> Handle.N
        nearBottom && withinX -> Handle.S
        nearLeft && withinY -> Handle.W
        nearRight && withinY -> Handle.E
        rect.contains(x, y) -> Handle.MOVE
        else -> null
    }
}
