package io.soo.bueongi.core.appinfo.api.controller

import io.soo.bueongi.core.appinfo.api.response.AppInfoResponse
import org.springframework.boot.info.BuildProperties
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/app-info")
class AppInfoController(
    buildProperties: BuildProperties,
) {
    private val serverVersion: String = buildProperties.version

    @GetMapping
    fun appInfo(): AppInfoResponse = AppInfoResponse(serverVersion = serverVersion)
}
