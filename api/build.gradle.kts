tasks.getByName("bootJar") { enabled = true }
tasks.getByName("bootRun") { enabled = true }
tasks.getByName("jar") { enabled = false }

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-facility"))
    implementation(project(":core:core-share"))
    implementation(project(":core:core-place"))
    implementation(project(":core:core-route"))
    implementation(project(":core:core-ads"))
    implementation(project(":core:core-ops"))
    implementation(project(":core:core-auth"))
    implementation(project(":storage:db-core"))
    implementation(project(":clients:client-tmap"))
    implementation(project(":clients:client-kakao"))
    implementation(project(":support:logging"))
    implementation(project(":support:monitoring"))
    implementation(project(":support:util"))

    implementation("org.springframework.boot:spring-boot-starter-web")

    implementation("org.springframework.boot:spring-boot-starter-security")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.jsonwebtoken:jjwt-api:0.12.6")

    testImplementation(testFixtures(project(":core:core-facility")))
    testImplementation(testFixtures(project(":core:core-share")))
}
