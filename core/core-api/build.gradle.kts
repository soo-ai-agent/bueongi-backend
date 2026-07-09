// 부팅 모듈 — 컨트롤러(api) + 유스케이스 오케스트레이션(domain) + 공통 에러(support/error).
tasks.getByName("bootJar") { enabled = true }
tasks.getByName("bootRun") { enabled = true }
tasks.getByName("jar") { enabled = false }

// dev 편의: 단일 모듈 시절처럼 backend/ 루트를 작업 디렉터리로 실행한다 —
// `bueongi.facility.data-dir` 기본값(../cdn)·.env.local 상대경로가 그대로 유효하다.
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":storage:db-core"))
    implementation(project(":clients:client-tmap"))
    implementation(project(":clients:client-kakao"))
    implementation(project(":support:logging"))
    implementation(project(":support:monitoring"))

    implementation("org.springframework.boot:spring-boot-starter-web")
}
