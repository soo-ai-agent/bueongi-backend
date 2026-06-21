import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val kotlinVersion: String by project
val springBootVersion: String by project
val springDependencyManagementVersion: String by project
val javaVersion: String by project

plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = property("projectGroup") as String
version = property("applicationVersion") as String

java.sourceCompatibility = JavaVersion.valueOf("VERSION_$javaVersion")

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // 위치 공유(share) 전용: Redis 키 share:{token}/location:{token} 의 HASH+TTL 저장.
    // Redis 미주입(로컬/CI/MVP)이면 RedisAutoConfiguration 을 제외하고 인메모리 폴백으로 동작한다.
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // RedisLocationStore 의 Redis 키 계약을 실 Redis 없이 검증하기 위한 Kotlin 친화 Mockito 래퍼(Mockito 5 호환).
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict")
        jvmTarget = javaVersion
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
