package io.soo.bueongi.core.api.controller.v1.response

import io.soo.bueongi.core.domain.share.ActiveShareView

data class ShareSessionsResponse(
    val sessions: List<ActiveShareView>,
)
