package io.soo.bueongi.core.appinfo.api.response

data class AppInfoResponse(
    val serverVersion: String,
) {
    companion object {
        fun from(serverVersion: String): AppInfoResponse {
            return AppInfoResponse(serverVersion = serverVersion)
        }
    }
}
