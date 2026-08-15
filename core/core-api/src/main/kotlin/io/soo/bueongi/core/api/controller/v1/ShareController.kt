package io.soo.bueongi.core.api.controller.v1

import io.soo.bueongi.core.api.controller.v1.request.CreateShareRequest
import io.soo.bueongi.core.api.controller.v1.response.CreateShareResponse
import io.soo.bueongi.core.api.controller.v1.request.EndShareRequest
import io.soo.bueongi.core.domain.share.LocationView
import io.soo.bueongi.core.domain.share.route.RouteView
import io.soo.bueongi.core.api.controller.v1.request.SaveRouteRequest
import io.soo.bueongi.core.domain.share.ShareService
import io.soo.bueongi.core.api.controller.v1.request.UpdateLocationRequest
import io.soo.bueongi.core.api.controller.v1.response.UpdateLocationResponse
import io.soo.bueongi.core.api.controller.v1.request.WatchRequest
import io.soo.bueongi.core.api.controller.v1.response.WatchResponse

import jakarta.validation.Valid
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/share")
class ShareController(
    private val service: ShareService,
    private val guardianPage: GuardianPage,
) {

    @PostMapping("/create")
    fun create(@Valid @RequestBody(required = false) request: CreateShareRequest?): CreateShareResponse =
        CreateShareResponse.from(service.create(request?.expiresInHours, request?.etaSeconds))

    @PostMapping("/{token}/location")
    fun updateLocation(
        @PathVariable token: String,
        @Valid @RequestBody request: UpdateLocationRequest,
    ): UpdateLocationResponse =
        UpdateLocationResponse.from(service.updateLocation(token, request.ownerSecret, request.toReport()))

    @GetMapping("/{token}/location")
    fun getLocation(@PathVariable token: String): LocationView = service.getLocation(token)

    @PostMapping("/{token}/route")
    fun saveRoute(
        @PathVariable token: String,
        @Valid @RequestBody request: SaveRouteRequest,
    ): UpdateLocationResponse =
        UpdateLocationResponse.from(service.saveRoute(token, request.ownerSecret, request.toStoredRoute()))

    @GetMapping("/{token}/route")
    fun getRoute(@PathVariable token: String): RouteView = service.getRoute(token)

    @PostMapping("/{token}/end")
    fun end(
        @PathVariable token: String,
        @Valid @RequestBody request: EndShareRequest,
    ) = service.end(token, request.ownerSecret)

    @PostMapping("/{token}/watching")
    fun watching(
        @PathVariable token: String,
        @Valid @RequestBody request: WatchRequest,
    ): WatchResponse = WatchResponse.from(service.getWatchingStatus(token, request.ownerSecret))

    @GetMapping("/{token}", produces = [MediaType.TEXT_HTML_VALUE])
    fun page(@PathVariable token: String): String = guardianPage.render(token)

}
