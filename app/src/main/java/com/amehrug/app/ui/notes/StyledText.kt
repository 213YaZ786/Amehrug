package com.amehrug.app.ui.notes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.amehrug.app.model.TextSpan
import com.amehrug.app.model.TextSpans

/**
 * The note body, drawn with its styles.
 *
 * The spans are normalised first, so a list from an old file or from a
 * Notally backup cannot reach outside the text and crash the layout.
 */
fun annotate(text: String, spans: List<TextSpan>, linkColor: Color): AnnotatedString {
    val runs = TextSpans.normalize(spans, text.length)
    if (runs.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        append(text)
        for (run in runs) {
            val decorations = ArrayList<TextDecoration>(2)
            if (run.strikethrough) decorations.add(TextDecoration.LineThrough)
            if (run.link) decorations.add(TextDecoration.Underline)
            addStyle(
                style = SpanStyle(
                    fontWeight = if (run.bold) FontWeight.Bold else null,
                    fontStyle = if (run.italic) FontStyle.Italic else null,
                    fontFamily = if (run.monospace) FontFamily.Monospace else null,
                    textDecoration = when (decorations.size) {
                        0 -> null
                        1 -> decorations[0]
                        else -> TextDecoration.combine(decorations)
                    },
                    color = if (run.link) linkColor else Color.Unspecified,
                ),
                start = run.start,
                end = run.end,
            )
        }
    }
}

/**
 * What lets a plain text field show styled text.
 *
 * The transformation changes no character, so the cursor sits exactly where
 * the text says it does and the mapping is the identity one. Styling through
 * the field's own value would mean carrying an AnnotatedString in and out of
 * every keystroke, which is both slower and easy to get wrong.
 */
class SpanTransformation(
    private val spans: List<TextSpan>,
    private val linkColor: Color,
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(annotate(text.text, spans, linkColor), OffsetMapping.Identity)

    // The field only reruns the transformation when this object is a
    // different one, so equality has to follow the spans.
    override fun equals(other: Any?): Boolean =
        other is SpanTransformation && other.spans == spans && other.linkColor == linkColor

    override fun hashCode(): Int = 31 * spans.hashCode() + linkColor.hashCode()
}
