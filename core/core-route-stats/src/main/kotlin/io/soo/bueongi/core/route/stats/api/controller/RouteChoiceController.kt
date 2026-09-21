package io.soo.bueongi.core.route.stats.api.controller

import io.soo.bueongi.core.route.stats.api.request.RouteChoiceRequest
import io.soo.bueongi.core.route.stats.domain.model.RouteChoiceSummary
import io.soo.bueongi.core.route.stats.domain.service.RouteChoiceService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
class RouteChoiceController(
    private val routeChoiceService: RouteChoiceService,
) {

    // 저장소가 실패를 삼키는 계약이라 201 을 약속할 수 없어 202 다.
    @PostMapping("/api/route-choices")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun record(@Valid @RequestBody request: RouteChoiceRequest) {
        routeChoiceService.record(request.toChoice())
    }

    @GetMapping("/api/admin/route-choices")
    fun routeChoices(): RouteChoiceSummary =
        routeChoiceService.summarize()
}
