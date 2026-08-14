package io.soo.bueongi.share

import jakarta.validation.Valid
import org.springframework.core.io.ClassPathResource
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
) {
    private val guardianHtmlTemplate: String by lazy {
        ClassPathResource("share/guardian.html").inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
    }

    @PostMapping("/create")
    fun create(@Valid @RequestBody(required = false) request: CreateShareRequest?): CreateShareResponse =
        service.create(request?.expiresInHours, request?.etaSeconds)

    @PostMapping("/{token}/location")
    fun updateLocation(
        @PathVariable token: String,
        @Valid @RequestBody request: UpdateLocationRequest,
    ): UpdateLocationResponse = service.updateLocation(token, request.ownerSecret, request.toReport())

    @GetMapping("/{token}/location")
    fun getLocation(@PathVariable token: String): LocationView = service.getLocation(token)

    @PostMapping("/{token}/route")
    fun saveRoute(
        @PathVariable token: String,
        @Valid @RequestBody request: SaveRouteRequest,
    ): UpdateLocationResponse = service.saveRoute(token, request.ownerSecret, request.toStoredRoute())

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
    ): WatchResponse = service.getWatchingStatus(token, request.ownerSecret)

    @GetMapping("/{token}", produces = [MediaType.TEXT_HTML_VALUE])
    fun page(@PathVariable token: String): String =
        guardianHtmlTemplate.replace(TOKEN_PLACEHOLDER, escapeJs(token))

    private fun escapeJs(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"").replace("<", "\\u003c").replace(">", "\\u003e")

    private companion object {
        const val TOKEN_PLACEHOLDER: String = "__SHARE_TOKEN__"
    }
}
