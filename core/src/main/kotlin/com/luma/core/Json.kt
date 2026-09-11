package com.luma.core

/** Small bounded JSON reader; no reflection, Android API or executable JavaScript. */
object Json {
    fun parse(text: String): Any? = Reader(text, false).read()
    internal fun initialState(text: String): Any? = Reader(text, true).read()
    fun stringify(value: Any?): String = buildString { write(value, 0) }
    private fun StringBuilder.write(value: Any?, depth: Int) {
        require(depth <= 128) { "JSON nesting too deep" }
        when (value) {
            null -> append("null")
            is String -> {
                append('"')
                value.forEach { c -> when (c) {
                    '"' -> append("\\\""); '\\' -> append("\\\\")
                    '\n' -> append("\\n"); '\r' -> append("\\r"); '\t' -> append("\\t")
                    else -> if (c < ' ') append("\\u%04x".format(c.code)) else append(c)
                } }
                append('"')
            }
            is Boolean -> append(value)
            is Number -> { require(value.toDouble().isFinite()); append(value.toString()) }
            is Map<*, *> -> { append('{'); value.entries.forEachIndexed { i, e ->
                require(e.key is String); if (i > 0) append(','); write(e.key, depth + 1); append(':'); write(e.value, depth + 1)
            }; append('}') }
            is Iterable<*> -> { append('['); value.forEachIndexed { i, v -> if (i > 0) append(','); write(v, depth + 1) }; append(']') }
            else -> error("Unsupported JSON value")
        }
    }
    private class Reader(val text: String, val allowUndefined: Boolean) {
        var pos = 0
        init { require(text.length <= 24 * 1024 * 1024) { "JSON too large" } }
        fun read(): Any? { val v = value(0); space(); require(pos == text.length) { "Trailing JSON input" }; return v }
        fun space() { while (pos < text.length && text[pos] in " \n\r\t") pos++ }
        fun take(c: Char): Boolean { space(); if (pos < text.length && text[pos] == c) { pos++; return true }; return false }
        fun value(depth: Int): Any? {
            require(depth <= 128); space(); require(pos < text.length) { "Incomplete JSON" }
            return when (text[pos]) {
                '"' -> string()
                '{' -> { pos++; val m = linkedMapOf<String, Any?>(); if (!take('}')) {
                    do { space(); require(pos < text.length && text[pos] == '"'); val k = string(); require(take(':')); m[k] = value(depth + 1) } while (take(','))
                    require(take('}'))
                }; m }
                '[' -> { pos++; val list = mutableListOf<Any?>(); if (!take(']')) {
                    do { list.add(value(depth + 1)) } while (take(',')); require(take(']'))
                }; list }
                't' -> literal("true", true)
                'f' -> literal("false", false)
                'n' -> literal("null", null)
                'u' -> { require(allowUndefined); literal("undefined", null) }
                else -> number()
            }
        }
        fun literal(token: String, result: Any?): Any? { require(text.startsWith(token, pos)); pos += token.length; return result }
        fun string(): String {
            require(text[pos++] == '"'); val out = StringBuilder()
            while (pos < text.length) {
                val c = text[pos++]
                if (c == '"') return out.toString()
                if (c == '\\') {
                    require(pos < text.length)
                    when (val e = text[pos++]) {
                        '"', '\\', '/' -> out.append(e)
                        'b' -> out.append('\b'); 'f' -> out.append('\u000c')
                        'n' -> out.append('\n'); 'r' -> out.append('\r'); 't' -> out.append('\t')
                        'u' -> { require(pos + 4 <= text.length); out.append(text.substring(pos, pos + 4).toInt(16).toChar()); pos += 4 }
                        else -> error("Invalid JSON escape")
                    }
                } else { require(c >= ' '); out.append(c) }
            }; error("Unterminated JSON string")
        }
        fun number(): Number {
            val start = pos
            if (text[pos] == '-') pos++
            require(pos < text.length)
            if (text[pos] == '0') pos++ else { require(text[pos] in '1'..'9'); while (pos < text.length && text[pos].isDigit()) pos++ }
            if (pos < text.length && text[pos] == '.') { pos++; val p = pos; while (pos < text.length && text[pos].isDigit()) pos++; require(pos > p) }
            if (pos < text.length && text[pos] in "eE") { pos++; if (pos < text.length && text[pos] in "+-") pos++; val p = pos; while (pos < text.length && text[pos].isDigit()) pos++; require(pos > p) }
            val raw = text.substring(start, pos)
            return raw.toLongOrNull() ?: raw.toDouble().also { require(it.isFinite()) }
        }
    }
}
@Suppress("UNCHECKED_CAST") fun Any?.obj(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()
fun Any?.arr(): List<Any?> = this as? List<Any?> ?: emptyList()
fun Any?.at(vararg path: String): Any? { var v: Any? = this; path.forEach { v = v.obj()[it] }; return v }
fun Any?.str(): String = when(this) { is String -> this; is Number -> toString(); else -> "" }
fun Any?.s(vararg path: String): String = at(*path).str()
fun Any?.list(vararg path: String): List<Any?> = at(*path).arr()
fun Any?.doubleOrNull(): Double? = when(this) {
    is Number -> toDouble().takeIf { it.isFinite() }
    is String -> { val t = trim().replace(",", ""); val factor = when(t.lastOrNull()) {'万' -> 1e4; '亿' -> 1e8; 'k','K' -> 1e3; 'm','M' -> 1e6; else -> 1.0}; (if(factor == 1.0) t else t.dropLast(1)).toDoubleOrNull()?.times(factor)?.takeIf { it.isFinite() } }
    else -> null
}
fun Any?.longOrNull(): Long? = if (this is Long) this else doubleOrNull()?.toLong()
fun Any?.n(vararg path: String): Long? = at(*path).longOrNull()
fun Any?.truth(): Boolean = this == true || this == 1 || this == 1L || this == "true" || this == "1"
fun fps(value: Any?): Double = if (value is String && value.contains('/')) {
    val p = value.split('/'); val d = p.getOrNull(1)?.toDoubleOrNull() ?: 0.0
    if(d > 0) (p[0].toDoubleOrNull() ?: 0.0) / d else 0.0
} else value.doubleOrNull() ?: 0.0

/** Extract only a balanced object/array assigned to a named global, without evaluating script. */
fun assignedJson(html: String, name: String): Any? {
    val marker = Regex(Regex.escape(name) + "\\s*=\\s*").find(html) ?: return null
    val start = marker.range.last + 1
    if (start >= html.length || html[start] !in "{[") return null
    var depth = 0; var quoted = false; var escape = false
    for(i in start until html.length) {
        val c = html[i]
        if(quoted) { if(escape) escape = false else if(c == '\\') escape = true else if(c == '"') quoted = false; continue }
        when(c) { '"' -> quoted = true; '{','[' -> depth++; '}',']' -> depth-- }
        if(depth == 0) return runCatching { Json.initialState(html.substring(start, i + 1)) }.getOrNull()
        if(depth > 128) return null
    }; return null
}
fun findObject(root: Any?, predicate: (Map<String, Any?>) -> Boolean): Map<String, Any?>? {
    val queue = java.util.ArrayDeque<Any>(); root?.let(queue::add)
    var nodes = 0
    while(queue.isNotEmpty() && nodes++ < 100000) {
        val node = queue.removeFirst()
        if(node is Map<*, *>) { val map = node.obj(); if(predicate(map)) return map; map.values.filterNotNull().forEach { if(it is Map<*, *> || it is List<*>) queue.add(it) } }
        else if(node is List<*>) node.filterNotNull().forEach { if(it is Map<*, *> || it is List<*>) queue.add(it) }
    }; return null
}
