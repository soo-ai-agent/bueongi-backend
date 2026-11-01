rootProject.name = "bueongi-backend"

include(
    "core:core-enum",
    "core:core-common",
    "core:core-safety-spot",
    "core:core-share",
    "core:core-place",
    "core:core-route",
    "core:core-admin-ops",
    "core:core-admin-safety-spot",
    "core:core-admin-auth",
    "core:core-app",
    "storage:db-safety-spot",
    "storage:db-share",
    "clients:client-place",
    "clients:client-tmap",
    "clients:client-tmap-place",
    "clients:client-slack",
    "clients:client-datagokr-security-lamp",
    "clients:client-safemap",
    "clients:client-safe182",
    "clients:client-safetydata-street-lamp",
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
    plugins {
        kotlin("plugin.jpa") version "1.9.25"
        kotlin("plugin.allopen") version "1.9.25"
    }
}
