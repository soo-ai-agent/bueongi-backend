plugins {
    kotlin("plugin.jpa")
    kotlin("plugin.allopen")
}
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    // DB 저장소(bueongi.share.db-url)의 기본 드라이버 — PostgreSQL 로 바꿀 때는 드라이버만 더한다.
    runtimeOnly("com.h2database:h2")

    testImplementation("org.assertj:assertj-core")
    // JpaShareSessionStore 테스트(@DataJpaTest)가 인메모리 H2 로 실SQL 을 돌린다.
    testRuntimeOnly("com.h2database:h2")
    testImplementation(kotlin("test"))
}
repositories {
    mavenCentral()
}
allOpen {
    annotation("javax.persistence.Entity")
    annotation("javax.persistence.Embeddable")
    annotation("javax.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.Embeddable")
    annotation("jakarta.persistence.MappedSuperclass")
}
