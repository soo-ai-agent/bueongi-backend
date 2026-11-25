plugins {
    id("java-test-fixtures")
}
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    testImplementation("org.assertj:assertj-core")
    testImplementation(kotlin("test"))
}
repositories {
    mavenCentral()
}
