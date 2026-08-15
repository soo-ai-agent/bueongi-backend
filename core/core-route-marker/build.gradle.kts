dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-route-safety"))
    implementation(project(":storage:db-core"))

    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation(testFixtures(project(":storage:db-core")))
}
