// Tmap 보행자 경로(E-1) 클라이언트 — 자립 모듈(다른 프로젝트 모듈에 의존하지 않음).
// 자체 좌표/경로 모델(TmapCoord/TmapRoute)을 반환하고, 도메인 매핑은 상위(core-api)가 한다.
dependencies {
    implementation("org.springframework:spring-web") // RestClient
    implementation("org.slf4j:slf4j-api")
}
