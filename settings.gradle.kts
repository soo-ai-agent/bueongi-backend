rootProject.name = "bueongi-backend"

include(
    "core:core-enum",
    "core:core-common",
    "core:core-safety-spot",
    "core:core-share",
    "core:core-place",
    "core:core-route",
    "core:core-route-stats",
    "core:core-ads",
    "core:core-ops",
    "core:core-auth",
    "api",
    "storage:db-core",
    "clients:client-tmap",
    "clients:client-kakao",
    "support:logging",
    "support:monitoring",
    "support:util",
)

pluginManagement {
    val kotlinVersion: String by settings
    val springBootVersion: String by settings
    val springDependencyManagementVersion: String by settings

    repositories {
        gradlePluginPortal()
        mavenCentral()
    }

    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "org.jetbrains.kotlin.jvm",
                "org.jetbrains.kotlin.plugin.spring" -> useVersion(kotlinVersion)
                "org.springframework.boot" -> useVersion(springBootVersion)
                "io.spring.dependency-management" -> useVersion(springDependencyManagementVersion)
            }
        }
    }
}
