dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-common"))
    implementation(project(":core:core-facility"))
    implementation(project(":clients:client-tmap"))
    implementation(project(":storage:db-core"))
    implementation(project(":support:monitoring"))
    implementation(project(":support:util"))

    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation(testFixtures(project(":core:core-facility")))
}
