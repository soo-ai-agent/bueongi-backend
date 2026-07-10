// 공공데이터포털(data.go.kr) 행안부 조회서비스 클라이언트 — 자립 모듈(다른 프로젝트 모듈에 의존하지 않음).
// 자체 시설점 모델(DataGoKrPoint)을 반환하고, 도메인(FacilityPoint) 매핑은 상위(core-api)가 한다.
dependencies {
    implementation("org.springframework:spring-web") // RestClient
    implementation("org.slf4j:slf4j-api")
}
