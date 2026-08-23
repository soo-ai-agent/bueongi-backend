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

    /**
     * 202 인 것은 저장소가 실패를 삼키는 계약이라 "만들었다(201)"를 약속할 수 없기 때문이다.
     * 접수만 알리고, 돌려줄 자원도 없다.
     */
    @PostMapping("/api/route-choices")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun record(@Valid @RequestBody request: RouteChoiceRequest) {
        routeChoiceService.record(request.toChoice())
    }

    /** 경로가 /api/admin 아래인 것은 관리자 화면 계약이다 — 경로 선택 기록이라 경로 모듈이 답한다. */
    @GetMapping("/api/admin/route-choices")
    fun routeChoices(): RouteChoiceSummary =
        routeChoiceService.summarize()
}
