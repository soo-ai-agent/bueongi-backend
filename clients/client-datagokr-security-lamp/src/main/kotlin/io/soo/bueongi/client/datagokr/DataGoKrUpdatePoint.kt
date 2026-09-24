package io.soo.bueongi.client.datagokr

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// 원천 표기는 YYYYMMDDHHMMSS 다 — 형식이 어긋나면 필터가 조용히 무시된다.
@JvmInline
value class DataGoKrUpdatePoint(val value: String) {
    init {
        require(value.length == LENGTH && value.all { it.isDigit() }) {
            "데이터갱신시점은 YYYYMMDDHHMMSS 14자리 숫자여야 한다."
        }
    }

    companion object {
        private const val LENGTH: Int = 14
        private val FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

        fun of(at: LocalDateTime): DataGoKrUpdatePoint = DataGoKrUpdatePoint(at.format(FORMAT))
    }
}
