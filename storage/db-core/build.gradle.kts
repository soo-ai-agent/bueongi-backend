// 저장 격리 모듈 — 위치 공유 세션 저장소(Redis/인메모리)와 안심시설 데이터 적재(파일)·공간 인덱스.
// DB(JPA) 대신 이 프로젝트의 실제 저장 기술(Redis HASH+TTL, CDN 정적 파일)을 담당한다.
dependencies {
    implementation(project(":core:core-enum"))

    // api: 상위(core-api)가 LocationStore 조건부 배선에서 Redis 타입(StringRedisTemplate 등)을 보게 노출.
    api("org.springframework.boot:spring-boot-starter-data-redis")
}
