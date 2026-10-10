package io.soo.bueongi.core.appinfo.domain.service

import io.soo.bueongi.core.appinfo.api.response.AppInfoResponse
import org.springframework.boot.info.BuildProperties
import org.springframework.stereotype.Service

@Service
class AppInfoService(
    buildProperties: BuildProperties,
) {
    private val serverVersion: String = buildProperties.version

    fun getAppInfo(): AppInfoResponse {
        return AppInfoResponse.from(serverVersion)
    }
}
