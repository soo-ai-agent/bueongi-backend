package io.soo.bueongi.api

import io.soo.bueongi.core.place.domain.service.RemotePlaceProvider
import io.soo.bueongi.core.share.domain.service.ShareService
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/app-info")
class AppInfoController(
    private val remoteProvider: RemotePlaceProvider,
    private val shareService: ShareService,
    @Value("\${bueongi.app-info.frontend-version:}")
    frontendVersion: String,
    // 빌드 정보 파일(build-info.properties) 없이 실행하면(IDE 클래스 빌드 등) null 이 가능함
    buildProperties: BuildProperties?,
) {
    private val frontendVersion: String = frontendVersion.trim()
    private val serverVersion: String = buildProperties?.version.orEmpty()
    private val serverBuiltAt: String = buildProperties?.time?.toString().orEmpty()

    @GetMapping
    fun appInfo(): AppInfoResponse {
        return AppInfoResponse(
            serverVersion = serverVersion,
            serverBuiltAt = serverBuiltAt,
            serverStatus = STATUS_UP,
            frontendVersion = frontendVersion,
            placeSource = remoteProvider.sourceLabel,
            locationStore = shareService.storeBackendLabel(),
        )
    }

    private companion object {
        const val STATUS_UP: String = "UP"
    }
}
