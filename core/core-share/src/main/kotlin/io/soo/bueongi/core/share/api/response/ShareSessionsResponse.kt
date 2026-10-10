package io.soo.bueongi.core.share.api.response

data class ShareSessionsResponse(
    val sessions: List<ActiveShareView>,
) {
    companion object {
        fun from(sessions: List<ActiveShareView>): ShareSessionsResponse {
            return ShareSessionsResponse(sessions = sessions)
        }
    }
}
