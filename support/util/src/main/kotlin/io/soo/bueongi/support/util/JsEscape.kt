package io.soo.bueongi.support.util

fun escapeForJs(value: String): String {
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("<", "\\u003c")
        .replace(">", "\\u003e")
}
