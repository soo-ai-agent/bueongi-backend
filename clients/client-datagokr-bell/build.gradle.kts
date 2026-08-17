dependencies {
    implementation(project(":clients:client-datagokr"))

    testImplementation(testFixtures(project(":clients:client-datagokr")))

    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-context")
}
