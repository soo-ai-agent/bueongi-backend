package io.soo.bueongi.core.appinfo.domain.service

import io.soo.bueongi.core.appinfo.api.response.AppInfoResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Service

@Service
class AppInfoService(
    buildProperties: BuildProperties,
    // 앱 강제·권장 업데이트 기준. 빈 값이면 정책 없음 — 앱이 판정을 건너뛴다.
    @Value("\${bueongi.app.min-supported-version:}")
    private val minSupportedAppVersion: String,
    @Value("\${bueongi.app.latest-version:}")
    private val latestAppVersion: String,
) {
    private val serverVersion: String = buildProperties.version

    fun get(): AppInfoResponse {
        return AppInfoResponse.from(serverVersion, minSupportedAppVersion, latestAppVersion)
    }
}
