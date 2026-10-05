package io.soo.bueongi.core.share.api.controller

import io.soo.bueongi.core.share.api.request.CreateShareRequest
import io.soo.bueongi.core.share.api.request.EndShareRequest
import io.soo.bueongi.core.share.api.request.SaveRouteRequest
import io.soo.bueongi.core.share.api.request.UpdateLocationRequest
import io.soo.bueongi.core.share.api.request.WatchRequest
import io.soo.bueongi.core.share.api.response.CreateShareResponse
import io.soo.bueongi.core.share.api.response.UpdateLocationResponse
import io.soo.bueongi.core.share.api.response.WatchResponse
import io.soo.bueongi.core.share.domain.model.LocationReport
import io.soo.bueongi.core.share.domain.model.LocationView
import io.soo.bueongi.core.share.domain.model.ShareLocationUpdated
import io.soo.bueongi.core.share.domain.route.model.RouteView
import io.soo.bueongi.core.share.domain.service.ShareService
import io.soo.bueongi.storage.db.core.share.model.StoredRoute
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
    fun create(
        @Valid @RequestBody(required = false) request: CreateShareRequest?,
    ): CreateShareResponse {
        return CreateShareResponse.from(service.create(request?.expiresInHours, request?.etaSeconds))
    }

    @PostMapping("/{token}/location")
    fun updateLocation(
        @PathVariable token: String,
        @Valid @RequestBody request: UpdateLocationRequest,
    ): UpdateLocationResponse {
        val report: LocationReport = request.toReport()
        val updated: ShareLocationUpdated = service.updateLocation(token, request.ownerSecret, report)
        return UpdateLocationResponse.from(updated)
    }

    @GetMapping("/{token}/location")
    fun getLocation(
        @PathVariable token: String,
    ): LocationView {
        return service.getLocation(token)
    }

    @PostMapping("/{token}/route")
    fun saveRoute(
        @PathVariable token: String,
        @Valid @RequestBody request: SaveRouteRequest,
    ): UpdateLocationResponse {
        val route: StoredRoute = request.toStoredRoute()
        val saved: ShareLocationUpdated = service.saveRoute(token, request.ownerSecret, route)
        return UpdateLocationResponse.from(saved)
    }

    @GetMapping("/{token}/route")
    fun getRoute(
        @PathVariable token: String,
    ): RouteView {
        return service.getRoute(token)
    }

    @PostMapping("/{token}/end")
    fun end(
        @PathVariable token: String,
        @Valid @RequestBody request: EndShareRequest,
    ) {
        service.end(token, request.ownerSecret)
    }

    @PostMapping("/{token}/watching")
    fun watching(
        @PathVariable token: String,
        @Valid @RequestBody request: WatchRequest,
    ): WatchResponse {
        return WatchResponse.from(service.getWatchingStatus(token, request.ownerSecret))
    }

    @GetMapping("/{token}", produces = [MediaType.TEXT_HTML_VALUE])
    fun page(
        @PathVariable token: String,
    ): String {
        return guardianPage.render(token)
    }
}
