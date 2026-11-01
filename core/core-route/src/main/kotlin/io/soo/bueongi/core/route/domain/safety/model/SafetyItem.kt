package io.soo.bueongi.core.route.domain.safety.model

enum class SafetyItem(
    val wireName: String,
) {
    CCTV("cctv"),
    LAMP("lamp"),
    BELL("bell"),
    SAFEHOUSE("safehouse"),
    POLICE("police"),
    SAFE_PATH("safePath"),
}
