package io.soo.bueongi.core.admin.safetyspot.domain.csv.service

import java.util.zip.GZIPInputStream

// 공공데이터 원본은 커서 .gz 로도 받는다 — gzip 매직(1f 8b)이면 풀어서 넘긴다.
internal fun gunzipIfNeeded(bytes: ByteArray): ByteArray {
    val isGzip: Boolean = bytes.size >= 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte()
    if (!isGzip) {
        return bytes
    }
    return GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
}
