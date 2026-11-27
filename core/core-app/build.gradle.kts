tasks.getByName("bootJar") { enabled = true }
tasks.getByName("bootRun") { enabled = true }
tasks.getByName("jar") { enabled = false }

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-safety-spot"))
    implementation(project(":core:core-share"))
    implementation(project(":core:core-place"))
    implementation(project(":core:core-route"))
    implementation(project(":core:core-admin-safety-spot"))
    implementation(project(":core:core-admin-auth"))
    implementation(project(":storage:db-safety-spot"))
    implementation(project(":storage:db-share"))
    testImplementation(testFixtures(project(":storage:db-share")))
    implementation(project(":clients:client-tmap"))
    implementation(project(":clients:client-tmap-place"))
    implementation(project(":support:logging"))
    implementation(project(":support:util"))

    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("io.jsonwebtoken:jjwt-api:0.12.6")

    testImplementation(testFixtures(project(":storage:db-safety-spot")))
    testImplementation(testFixtures(project(":core:core-share")))
}

springBoot {
    buildInfo()
}
