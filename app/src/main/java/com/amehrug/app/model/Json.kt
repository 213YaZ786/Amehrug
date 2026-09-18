package com.amehrug.app.model

/**
 * A small JSON writer and reader, on purpose.
 *
 * The backup format needs JSON and nothing more. A library would add a
 * dependency, a compiler plugin and a version to follow, for a hundred lines
 * of code that can be checked here. Reading is strict and bounded: depth,
 * length and shape are all limited, because this parser reads a file that
 * came from outside the app.
 */
sealed interface JsonValue {
    data object Null : JsonValue

    data class Bool(val value: Boolean) : JsonValue

    data class Num(val value: Double) : JsonValue

    data class Str(val value: String) : JsonValue

    data class Arr(val items: List<JsonValue>) : JsonValue

    data class Obj(val fields: Map<String, JsonValue>) : JsonValue
}

fun JsonValue?.asObject(): JsonValue.Obj? = this as? JsonValue.Obj

fun JsonValue?.asArray(): List<JsonValue> = (this as? JsonValue.Arr)?.items ?: emptyList()

fun JsonValue?.asString(default: String = ""): String = (this as? JsonValue.Str)?.value ?: default

fun JsonValue?.asLong(default: Long = 0): Long = (this as? JsonValue.Num)?.value?.toLong() ?: default

fun JsonValue?.asInt(default: Int = 0): Int = (this as? JsonValue.Num)?.value?.toInt() ?: default

fun JsonValue?.asBool(default: Boolean = false): Boolean = (this as? JsonValue.Bool)?.value ?: default

fun JsonValue.Obj.get(name: String): JsonValue? = fields[name]

object Json {
    const val MAX_DEPTH = 32
    const val MAX_LENGTH = 64 * 1024 * 1024

    fun write(value: JsonValue): String = StringBuilder().also { write(value, it) }.toString()

    private fun write(value: JsonValue, out: StringBuilder) {
        when (value) {
            is JsonValue.Null -> out.append("null")
            is JsonValue.Bool -> out.append(if (value.value) "true" else "false")
            is JsonValue.Num -> {
                val number = value.value
                if (number == number.toLong().toDouble()) {
                    out.append(number.toLong().toString())
                } else {
                    out.append(number.toString())
                }
            }
            is JsonValue.Str -> writeString(value.value, out)
            is JsonValue.Arr -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    write(item, out)
                }
                out.append(']')
            }
            is JsonValue.Obj -> {
                out.append('{')
                var first = true
                for ((name, field) in value.fields) {
                    if (!first) out.append(',')
                    first = false
                    writeString(name, out)
                    out.append(':')
                    write(field, out)
                }
                out.append('}')
            }
        }
    }

    private fun writeString(text: String, out: StringBuilder) {
        out.append('"')
        for (char in text) {
            when {
                char == '"' -> out.append("\\\"")
                char == '\\' -> out.append("\\\\")
                char == '\n' -> out.append("\\n")
                char == '\r' -> out.append("\\r")
                char == '\t' -> out.append("\\t")
                char < ' ' || char == '\u2028' || char == '\u2029' ->
                    out.append("\\u").append(char.code.toString(16).padStart(4, '0'))
                else -> out.append(char)
            }
        }
        out.append('"')
    }

    /** Throws [IllegalArgumentException] on anything that is not valid JSON. */
    fun parse(text: String): JsonValue {
        require(text.length <= MAX_LENGTH) { "json too large" }
        val reader = Reader(text)
        reader.skipSpace()
        val value = reader.readValue(0)
        reader.skipSpace()
        require(reader.atEnd()) { "trailing characters at ${reader.position}" }
        return value
    }

    private class Reader(private val text: String) {
        var position = 0
            private set

        fun atEnd(): Boolean = position >= text.length

        fun skipSpace() {
            while (position < text.length && text[position].isWhitespace()) position += 1
        }

        fun readValue(depth: Int): JsonValue {
            require(depth <= MAX_DEPTH) { "json nested too deep" }
            require(position < text.length) { "json ends too early" }
            return when (val char = text[position]) {
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                '"' -> JsonValue.Str(readString())
                't' -> literal("true", JsonValue.Bool(true))
                'f' -> literal("false", JsonValue.Bool(false))
                'n' -> literal("null", JsonValue.Null)
                else -> {
                    require(char == '-' || char.isDigit()) { "unexpected character at $position" }
                    readNumber()
                }
            }
        }

        private fun literal(word: String, value: JsonValue): JsonValue {
            require(text.startsWith(word, position)) { "unexpected word at $position" }
            position += word.length
            return value
        }

        private fun readObject(depth: Int): JsonValue.Obj {
            expect('{')
            val fields = LinkedHashMap<String, JsonValue>()
            skipSpace()
            if (peek() == '}') {
                position += 1
                return JsonValue.Obj(fields)
            }
            while (true) {
                skipSpace()
                val name = readString()
                skipSpace()
                expect(':')
                skipSpace()
                fields[name] = readValue(depth + 1)
                skipSpace()
                when (peek()) {
                    ',' -> position += 1
                    '}' -> {
                        position += 1
                        return JsonValue.Obj(fields)
                    }
                    else -> throw IllegalArgumentException("expected , or } at $position")
                }
            }
        }

        private fun readArray(depth: Int): JsonValue.Arr {
            expect('[')
            val items = ArrayList<JsonValue>()
            skipSpace()
            if (peek() == ']') {
                position += 1
                return JsonValue.Arr(items)
            }
            while (true) {
                skipSpace()
                items.add(readValue(depth + 1))
                skipSpace()
                when (peek()) {
                    ',' -> position += 1
                    ']' -> {
                        position += 1
                        return JsonValue.Arr(items)
                    }
                    else -> throw IllegalArgumentException("expected , or ] at $position")
                }
            }
        }

        private fun readString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                require(position < text.length) { "string is not closed" }
                when (val char = text[position]) {
                    '"' -> {
                        position += 1
                        return out.toString()
                    }
                    '\\' -> {
                        position += 1
                        require(position < text.length) { "escape is not finished" }
                        when (val escape = text[position]) {
                            '"' -> out.append('"')
                            '\\' -> out.append('\\')
                            '/' -> out.append('/')
                            'b' -> out.append('\b')
                            'f' -> out.append('\u000C')
                            'n' -> out.append('\n')
                            'r' -> out.append('\r')
                            't' -> out.append('\t')
                            'u' -> {
                                require(position + 4 < text.length) { "short unicode escape" }
                                val code = text.substring(position + 1, position + 5).toIntOrNull(16)
                                requireNotNull(code) { "bad unicode escape at $position" }
                                out.append(code.toChar())
                                position += 4
                            }
                            else -> throw IllegalArgumentException("bad escape $escape at $position")
                        }
                        position += 1
                    }
                    else -> {
                        require(char >= ' ') { "control character in string at $position" }
                        out.append(char)
                        position += 1
                    }
                }
            }
        }

        private fun readNumber(): JsonValue.Num {
            val start = position
            if (peek() == '-') position += 1
            while (position < text.length && (text[position].isDigit() || text[position] in ".eE+-")) {
                position += 1
            }
            val number = text.substring(start, position).toDoubleOrNull()
            requireNotNull(number) { "bad number at $start" }
            require(!number.isNaN() && !number.isInfinite()) { "number out of range at $start" }
            return JsonValue.Num(number)
        }

        private fun peek(): Char {
            require(position < text.length) { "json ends too early" }
            return text[position]
        }

        private fun expect(char: Char) {
            require(position < text.length && text[position] == char) { "expected $char at $position" }
            position += 1
        }
    }
}
