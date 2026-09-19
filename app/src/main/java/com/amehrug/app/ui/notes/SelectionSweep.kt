package com.amehrug.app.ui.notes

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.dp

/**
 * One gesture detector for the whole wall, rather than one per card.
 *
 * Doing it per card does not work here. A card sits inside the scrolling
 * container, so a card that claims the long press also claims the events the
 * sweep needs, and the two detectors race on the same timeout. Reading the
 * gesture once at the grid level removes the race and gives every position in
 * a single coordinate space, the one the grid itself reports its items in.
 *
 * Three outcomes from one press:
 *  - up before the long press timeout and without travel, that is a tap
 *  - travel before the timeout, that is a scroll, so nothing is consumed and
 *    the grid underneath keeps it
 *  - the timeout with the finger still down, that is a selection sweep. From
 *    there every move is consumed, so the grid cannot scroll under the finger,
 *    and the screen switches its own scrolling off for the same reason.
 */
private val EDGE_ZONE = 96.dp

suspend fun PointerInputScope.detectSelectionGestures(
    onTap: (Offset) -> Unit,
    onSweepStart: (Offset) -> Unit,
    onSweepMove: (Offset) -> Unit,
    onEdge: (Float) -> Unit,
    onSweepEnd: () -> Unit,
) {
    val zone = EDGE_ZONE.toPx()
    awaitEachGesture {
        // requireUnconsumed is false so that a press already seen by
        // something else still opens a gesture here.
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        var ended = false
        var tapped = false

        // Null means the timeout ran out with the finger still down and
        // still, which is exactly a long press.
        val settled = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (!ended) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) {
                    ended = true
                } else if (change.changedToUpIgnoreConsumed()) {
                    tapped = true
                    ended = true
                } else if ((change.position - down.position).getDistance() > slop) {
                    ended = true
                }
            }
            true
        }

        if (settled != null) {
            if (tapped) onTap(down.position)
        } else {
            onSweepStart(down.position)
            var running = true
            while (running) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || change.changedToUpIgnoreConsumed()) {
                    running = false
                } else {
                    change.consume()
                    onSweepMove(change.position)
                    // Only the direction travels out of here. A continuous
                    // speed would restart the scrolling effect on every
                    // frame for no gain.
                    onEdge(
                        when {
                            change.position.y < zone -> -1f
                            change.position.y > size.height - zone -> 1f
                            else -> 0f
                        },
                    )
                }
            }
            onEdge(0f)
            onSweepEnd()
        }
    }
}

/**
 * Where every visible card is, in the window's own coordinates.
 *
 * The first version of this asked the grid where its items were. Those
 * offsets leave out the padding the grid keeps at the top for the search
 * pill, about the height of a card, so every touch landed one card too low
 * and holding a card selected its neighbour. Rather than add that padding
 * back and trust a convention that cannot be checked here, each card now
 * reports the rectangle it actually occupies.
 *
 * Positions are kept relative to the window, not to the grid, because a card
 * and the grid report their own positions at different moments during a
 * layout pass. Converting the finger once, at the moment it is asked about,
 * removes that ordering from the picture entirely.
 */
class CardBounds {
    private val rects = HashMap<Long, Rect>()

    /**
     * The node that reads the gesture. Held here rather than in a state so
     * that a layout pass never asks for a recomposition just by reporting a
     * position it has already reported.
     */
    var origin: LayoutCoordinates? = null

    fun put(id: Long, coordinates: LayoutCoordinates) {
        if (!coordinates.isAttached) return
        // localToRoot is a member of LayoutCoordinates. positionInRoot is an
        // extension and needs its own import, which is what broke the build.
        val topLeft = coordinates.localToRoot(Offset.Zero)
        rects[id] = Rect(
            left = topLeft.x,
            top = topLeft.y,
            right = topLeft.x + coordinates.size.width,
            bottom = topLeft.y + coordinates.size.height,
        )
    }

    /** A card that leaves the screen takes its rectangle with it. */
    fun forget(id: Long) {
        rects.remove(id)
    }

    /** The note under [point], which is given in [origin]'s coordinates. */
    fun at(point: Offset): Long? {
        val grid = origin
        if (grid == null || !grid.isAttached) return null
        val inRoot = grid.localToRoot(point)
        for ((id, rect) in rects) {
            if (rect.contains(inRoot)) return id
        }
        return null
    }
}
