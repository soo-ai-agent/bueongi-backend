// 관측 모듈 — actuator 헬스/인포(monitoring.yml)와 운영 알림(Slack webhook)을 제공한다.
dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework:spring-web") // RestClient(Slack webhook 발송)
    implementation("org.slf4j:slf4j-api")
}
