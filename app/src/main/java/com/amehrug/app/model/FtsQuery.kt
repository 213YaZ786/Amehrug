package com.amehrug.app.model

/**
 * Turns what the user typed into a safe FTS4 MATCH expression.
 *
 * Every word becomes a quoted prefix term, so "réunion lu" finds
 * "Réunion lundi". Quoting keeps AND, OR, NOT and NEAR from acting as
 * operators, and only letters and digits survive, so no user input can
 * change the shape of the query. Accents and case are folded by the
 * unicode61 tokenizer of the table, not here.
 */
object FtsQuery {
    const val MAX_TERMS = 10
    const val MAX_TERM_LENGTH = 64

    fun build(input: String): String? {
        val terms = ArrayList<String>()
        val current = StringBuilder()
        fun flush() {
            if (current.isNotEmpty() && terms.size < MAX_TERMS) {
                terms.add(current.toString().take(MAX_TERM_LENGTH))
            }
            current.setLength(0)
        }
        for (char in input) {
            if (char.isLetterOrDigit()) current.append(char) else flush()
        }
        flush()
        if (terms.isEmpty()) return null
        return terms.joinToString(separator = " ") { term -> "\"$term*\"" }
    }
}
