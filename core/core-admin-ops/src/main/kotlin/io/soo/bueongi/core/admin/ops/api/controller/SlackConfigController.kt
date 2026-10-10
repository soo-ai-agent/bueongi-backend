package io.soo.bueongi.core.admin.ops.api.controller

import io.soo.bueongi.core.admin.ops.api.request.SlackConfigUpdateRequest
import io.soo.bueongi.core.admin.ops.api.response.SlackConfigResponse
import io.soo.bueongi.core.admin.ops.api.response.SlackTestResponse
import io.soo.bueongi.core.admin.ops.domain.slack.model.SlackConfigUpdate
import io.soo.bueongi.core.admin.ops.domain.slack.service.SlackConfigService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/slack-config")
class SlackConfigController(
    private val slackConfigService: SlackConfigService,
) {
    @GetMapping
    fun slackConfig(): SlackConfigResponse {
        return slackConfigService.view()
    }

    @PutMapping
    fun updateSlackConfig(
        @Valid @RequestBody request: SlackConfigUpdateRequest,
    ): SlackConfigResponse {
        val update: SlackConfigUpdate = request.toUpdate()
        return slackConfigService.update(update)
    }

    @PostMapping("/test")
    fun testSlackConfig(): SlackTestResponse {
        return slackConfigService.test()
    }
}
