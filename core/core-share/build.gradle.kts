plugins {
    id("java-test-fixtures")
}

dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-common"))
    implementation(project(":storage:db-share"))
    // SharePolicy 의 좌표 거리 계산(GeoPoint)이 쓴다 — core-common 전이에 기대지 않고 명시한다.
    implementation(project(":storage:db-safety-spot"))
    implementation(project(":support:logging"))
    implementation(project(":support:util"))

    implementation("org.springframework.boot:spring-boot-starter-web")
}
