package com.amehrug.app.model

// Pure Kotlin, no Android import, so it runs under kotlinc and is tested
// before delivery.

/**
 * The styles a run of text can carry. The bit is an implementation detail of
 * this file and is never stored: the database keeps one boolean per style.
 */
enum class NoteStyle(val bit: Int) {
    BOLD(1),
    ITALIC(2),
    MONOSPACE(4),
    STRIKETHROUGH(8),
    LINK(16),
}

/**
 * Spans, handled one character at a time.
 *
 * Every operation explodes the span list into one mask per character, works
 * on the masks, and rebuilds the runs. Merging and splitting ranges by hand
 * is where this kind of code goes wrong, and a note is short enough that a
 * mask per character costs nothing.
 *
 * The rebuilt list is always sorted, non overlapping, gap free over the
 * styled parts, and carries no empty or unstyled run.
 */
object TextSpans {

    fun maskOf(span: TextSpan): Int {
        var mask = 0
        if (span.bold) mask = mask or NoteStyle.BOLD.bit
        if (span.italic) mask = mask or NoteStyle.ITALIC.bit
        if (span.monospace) mask = mask or NoteStyle.MONOSPACE.bit
        if (span.strikethrough) mask = mask or NoteStyle.STRIKETHROUGH.bit
        if (span.link) mask = mask or NoteStyle.LINK.bit
        return mask
    }

    private fun spanOf(start: Int, end: Int, mask: Int) = TextSpan(
        start = start,
        end = end,
        bold = mask and NoteStyle.BOLD.bit != 0,
        italic = mask and NoteStyle.ITALIC.bit != 0,
        monospace = mask and NoteStyle.MONOSPACE.bit != 0,
        strikethrough = mask and NoteStyle.STRIKETHROUGH.bit != 0,
        link = mask and NoteStyle.LINK.bit != 0,
    )

    /**
     * One mask per character. Anything reaching outside the text is clipped,
     * which is what keeps a span list from an older or broken file harmless.
     */
    fun explode(spans: List<TextSpan>, length: Int): IntArray {
        val masks = IntArray(if (length > 0) length else 0)
        if (masks.isEmpty()) return masks
        for (span in spans) {
            val mask = maskOf(span)
            if (mask == 0) continue
            var from = span.start
            var to = span.end
            if (from > to) {
                val swap = from
                from = to
                to = swap
            }
            if (from < 0) from = 0
            if (to > masks.size) to = masks.size
            var i = from
            while (i < to) {
                masks[i] = masks[i] or mask
                i++
            }
        }
        return masks
    }

    /** The runs of equal mask, skipping the unstyled ones. */
    fun implode(masks: IntArray): List<TextSpan> {
        val out = ArrayList<TextSpan>()
        var i = 0
        while (i < masks.size) {
            val mask = masks[i]
            var j = i + 1
            while (j < masks.size && masks[j] == mask) j++
            if (mask != 0) out.add(spanOf(i, j, mask))
            i = j
        }
        return out
    }

    /** Sorted, merged, clipped. Safe to call on anything. */
    fun normalize(spans: List<TextSpan>, length: Int): List<TextSpan> =
        implode(explode(spans, length))

    /** The styles carried by the character before [index], none at the start. */
    fun maskBefore(spans: List<TextSpan>, length: Int, index: Int): Int {
        if (index <= 0) return 0
        val masks = explode(spans, length)
        val at = index - 1
        return if (at < masks.size) masks[at] else 0
    }

    /**
     * Whether the whole range already carries the style. An empty range
     * answers for the character before the cursor, which is what a toolbar
     * button has to light up on.
     */
    fun covers(spans: List<TextSpan>, length: Int, start: Int, end: Int, style: NoteStyle): Boolean {
        val from = minOf(start, end).coerceIn(0, length)
        val to = maxOf(start, end).coerceIn(0, length)
        if (from == to) return maskBefore(spans, length, from) and style.bit != 0
        val masks = explode(spans, length)
        var i = from
        while (i < to) {
            if (masks[i] and style.bit == 0) return false
            i++
        }
        return true
    }

    /**
     * Turn a style on over the range, or off when the whole range already
     * carries it. That is the rule every editor uses and the only one that
     * makes a single button both apply and remove.
     */
    fun toggle(
        spans: List<TextSpan>,
        length: Int,
        start: Int,
        end: Int,
        style: NoteStyle,
    ): List<TextSpan> {
        val from = minOf(start, end).coerceIn(0, length)
        val to = maxOf(start, end).coerceIn(0, length)
        if (from == to) return normalize(spans, length)
        val masks = explode(spans, length)
        val remove = covers(spans, length, from, to, style)
        var i = from
        while (i < to) {
            masks[i] = if (remove) masks[i] and style.bit.inv() else masks[i] or style.bit
            i++
        }
        return implode(masks)
    }

    /**
     * Carry the spans across an edit of the text.
     *
     * The edit is read back from the two strings rather than from the field,
     * as the common prefix, the common suffix, and whatever sits between
     * them. That covers typing, deleting, pasting, and a replaced selection
     * without the screen having to tell this function which one happened.
     *
     * Inserted characters take the style of the character on their left, so
     * typing at the end of a bold word stays bold. [pending] overrides that,
     * and is how a button pressed with no selection styles what comes next.
     * A pending mask of -1 means nothing is pending.
     */
    fun afterEdit(
        spans: List<TextSpan>,
        old: String,
        new: String,
        pending: Int = -1,
    ): List<TextSpan> {
        if (old == new) return normalize(spans, new.length)
        val masks = explode(spans, old.length)

        var prefix = 0
        val shortest = minOf(old.length, new.length)
        while (prefix < shortest && old[prefix] == new[prefix]) prefix++

        var suffix = 0
        while (
            suffix < shortest - prefix &&
            old[old.length - 1 - suffix] == new[new.length - 1 - suffix]
        ) {
            suffix++
        }

        val inserted = new.length - prefix - suffix
        val carried = if (pending >= 0) {
            pending
        } else if (prefix > 0 && prefix - 1 < masks.size) {
            masks[prefix - 1]
        } else {
            0
        }

        val out = IntArray(new.length)
        var i = 0
        while (i < prefix) {
            out[i] = masks[i]
            i++
        }
        var j = 0
        while (j < inserted) {
            out[prefix + j] = carried
            j++
        }
        var k = 0
        while (k < suffix) {
            out[new.length - suffix + k] = masks[old.length - suffix + k]
            k++
        }
        return implode(out)
    }
}
