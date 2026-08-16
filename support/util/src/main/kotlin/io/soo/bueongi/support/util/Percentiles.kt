package io.soo.bueongi.support.util

import kotlin.math.ceil

fun nearestRankPercentile(sortedAscending: List<Long>, percentile: Int): Long {
    if (sortedAscending.isEmpty()) {
        return 0L
    }
    val rank: Int = ceil(percentile / 100.0 * sortedAscending.size).toInt()
    val index: Int = rank.coerceIn(1, sortedAscending.size) - 1
    return sortedAscending[index]
}
