rootProject.name = "bueongi-backend"

include(
    "core:core-enum",
    "core:core-common",
    "core:core-api",
    "storage:db-core",
    "domain:place",
    "domain:facility",
    "domain:route",
    "domain:share",
    "domain:admin",
    "clients:client-tmap",
    "clients:client-kakao",
    "support:logging",
    "support:monitoring",
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
