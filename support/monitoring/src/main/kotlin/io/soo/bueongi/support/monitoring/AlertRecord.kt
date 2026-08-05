package io.soo.bueongi.support.monitoring

/** 장애 알림 이력 1건 — 발생 시각(epoch ms)·스로틀 key·제목·상세. */
data class AlertRecord(
    val atEpochMs: Long,
    val key: String,
    val title: String,
    val detail: String,
)
