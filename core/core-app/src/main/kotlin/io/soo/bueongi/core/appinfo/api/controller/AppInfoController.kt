package io.soo.bueongi.core.appinfo.api.controller

import io.soo.bueongi.core.appinfo.api.response.AppInfoResponse
import io.soo.bueongi.core.appinfo.domain.service.AppInfoService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/app-info")
class AppInfoController(
    private val appInfoService: AppInfoService,
) {
    @GetMapping
    fun appInfo(): AppInfoResponse {
        return appInfoService.getAppInfo()
    }
}
