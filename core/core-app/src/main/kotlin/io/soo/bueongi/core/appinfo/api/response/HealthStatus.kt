package io.soo.bueongi.core.appinfo.api.response

data class HealthStatus(
    val status: String,
    val service: String,
    val locationStore: String,
) {
    companion object {
        fun from(service: String, locationStore: String): HealthStatus {
            return HealthStatus(status = "UP", service = service, locationStore = locationStore)
        }
    }
}
