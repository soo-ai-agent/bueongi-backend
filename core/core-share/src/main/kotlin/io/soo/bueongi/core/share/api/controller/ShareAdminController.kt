package io.soo.bueongi.core.share.api.controller

import io.soo.bueongi.core.share.api.response.LocationView
import io.soo.bueongi.core.share.api.response.RouteView
import io.soo.bueongi.core.share.api.response.ShareSessionsResponse
import io.soo.bueongi.core.share.api.response.ShareStatusResponse
import io.soo.bueongi.core.share.domain.service.ShareService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin")
class ShareAdminController(
    private val shareService: ShareService,
) {
    @GetMapping("/share-status")
    fun status(): ShareStatusResponse {
        return shareService.getStatus()
    }

    @GetMapping("/share-sessions")
    fun sessions(): ShareSessionsResponse {
        return shareService.listActive()
    }

    @GetMapping("/share-sessions/{token}/location")
    fun sessionLocation(
        @PathVariable token: String,
    ): LocationView {
        return shareService.getLocationForAdmin(token)
    }

    @GetMapping("/share-sessions/{token}/route")
    fun sessionRoute(
        @PathVariable token: String,
    ): RouteView {
        return shareService.getRoute(token)
    }
}
