// 생활안전지도(safemap.go.kr) 치안시설 클라이언트 — 자립 모듈(다른 프로젝트 모듈에 의존하지 않음).
// 자체 시설 모델(SafemapPoliceFacility, WGS84 변환·검증 완료)을 반환하고, 도메인 매핑은 상위(core-api)가 한다.
dependencies {
    implementation("org.springframework:spring-web") // RestClient
    implementation("org.slf4j:slf4j-api")
}
