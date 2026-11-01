package io.soo.bueongi.core.appinfo.api.response

data class AppInfoResponse(
    val serverVersion: String,
    val minSupportedAppVersion: String,
    val latestAppVersion: String,
) {
    companion object {
        fun from(serverVersion: String, minSupportedAppVersion: String, latestAppVersion: String): AppInfoResponse {
            return AppInfoResponse(
                serverVersion = serverVersion,
                minSupportedAppVersion = minSupportedAppVersion,
                latestAppVersion = latestAppVersion,
            )
        }
    }
}
