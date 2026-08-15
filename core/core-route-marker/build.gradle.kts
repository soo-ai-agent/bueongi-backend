dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-route-safety"))
    implementation(project(":storage:db-core"))
    implementation(project(":support:monitoring"))

    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation(testFixtures(project(":storage:db-core")))
}
