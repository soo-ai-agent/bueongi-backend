rootProject.name = "bueongi-backend"

// soo-spring-kotlin-starter 와 동일한 모듈 지형:
// core(부팅·도메인) · storage(저장 격리) · clients(외부 API) · support(로깅/관측).
include(
    "core:core-enum",
    "core:core-api",
    "storage:db-core",
    "clients:client-tmap",
    "clients:client-kakao",
    "clients:client-datagokr",
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
