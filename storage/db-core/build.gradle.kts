// 저장 격리 모듈 — 위치 공유 세션 저장소(Redis/인메모리)와 안심시설 데이터 적재(파일)·공간 인덱스.
// DB(JPA) 대신 이 프로젝트의 실제 저장 기술(Redis HASH+TTL, CDN 정적 파일)을 담당한다.
dependencies {
    implementation(project(":core:core-enum"))

    // Redis 배선(ShareStoreConfig)이 이 모듈 안에 있으므로 저장 기술 타입을 상위에 노출하지 않는다(implementation).
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
}
