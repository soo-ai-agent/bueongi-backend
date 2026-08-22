package io.soo.bueongi.api.config

import io.soo.bueongi.core.enums.ExternalApiProvider

/**
 * 호스트로 원천을 가른다. 새 클라이언트를 붙이면 여기에 한 줄 늘린다 —
 * 빠뜨리면 [ExternalApiProvider.UNKNOWN] 으로 모여 관리자 화면에 드러난다.
 */
fun providerOfHost(host: String?): ExternalApiProvider {
    if (host == null) {
        return ExternalApiProvider.UNKNOWN
    }
    val lower: String = host.lowercase()
    for ((marker, provider) in HOST_MARKERS) {
        if (lower.contains(marker)) {
            return provider
        }
    }
    return ExternalApiProvider.UNKNOWN
}

/** 실제 호스트에서 뽑았다 — Tmap 은 이름에 tmap 이 없어(apis.openapi.sk.com) 호스트를 그대로 적는다. */
private val HOST_MARKERS: List<Pair<String, ExternalApiProvider>> = listOf(
    "openapi.sk.com" to ExternalApiProvider.TMAP,
    "kakao" to ExternalApiProvider.KAKAO,
    "safetydata" to ExternalApiProvider.SAFETYDATA,
    "safemap" to ExternalApiProvider.SAFEMAP,
    "safe182" to ExternalApiProvider.SAFE182,
    "slack" to ExternalApiProvider.SLACK,
    // data.go.kr 은 마지막에 둔다 — 다른 원천도 이 도메인을 쓰는 경우가 있어 먼저 걸리면 안 된다.
    "data.go.kr" to ExternalApiProvider.DATA_GO_KR,
)
