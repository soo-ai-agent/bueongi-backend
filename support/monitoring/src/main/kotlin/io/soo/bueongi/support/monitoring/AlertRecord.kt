package io.soo.bueongi.support.monitoring

data class AlertRecord(
    val atEpochMs: Long,
    val key: String,
    val title: String,
    val detail: String,
)
