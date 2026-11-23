package io.soo.bueongi.support.util

fun <T> List<T>.pickEvenly(count: Int): List<T> {
    if (count >= size) {
        return this
    }
    val picked: ArrayList<T> = ArrayList(count)
    for (slot in 0 until count) {
        picked.add(this[slot * size / count])
    }
    return picked
}
