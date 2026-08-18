dependencies {
    implementation(project(":clients:client-datagokr-security-lamp"))
    implementation(project(":clients:client-datagokr"))
    implementation(project(":clients:client-safetydata-street-lamp"))
    implementation(project(":clients:client-safemap"))
    implementation(project(":clients:client-safe182"))

    implementation(project(":core:core-enum"))
    implementation(project(":core:core-common"))
    implementation(project(":storage:db-core"))
    implementation(project(":support:util"))

    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation(testFixtures(project(":storage:db-core")))
}
