package io.soo.bueongi.core.admin.api.response

import io.soo.bueongi.core.share.domain.ActiveShareView

data class ShareSessionsResponse(
    val sessions: List<ActiveShareView>,
)
