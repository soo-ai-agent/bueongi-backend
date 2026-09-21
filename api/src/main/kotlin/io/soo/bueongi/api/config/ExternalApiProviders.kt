package io.soo.bueongi.api.config

import io.soo.bueongi.core.enums.ExternalApiProvider

fun providerOfHost(host: String?): ExternalApiProvider {
    if (host == null) {
        return ExternalApiProvider.UNKNOWN
    }
    val lower: String = host.lowercase()
    for ((marker, provider) in HOST_MARKERS_IN_MATCH_ORDER) {
        if (lower.contains(marker)) {
            return provider
        }
    }
    return ExternalApiProvider.UNKNOWN
}

private val HOST_MARKERS_IN_MATCH_ORDER: List<Pair<String, ExternalApiProvider>> =
    listOf(
        // Tmap 의 실제 호스트에는 tmap 이 들어가지 않는다.
        "openapi.sk.com" to ExternalApiProvider.TMAP,
        "kakao" to ExternalApiProvider.KAKAO,
        "safetydata" to ExternalApiProvider.SAFETYDATA,
        "safemap" to ExternalApiProvider.SAFEMAP,
        "safe182" to ExternalApiProvider.SAFE182,
        "slack" to ExternalApiProvider.SLACK,
        "data.go.kr" to ExternalApiProvider.DATA_GO_KR,
    )
