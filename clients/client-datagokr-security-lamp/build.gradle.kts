dependencies {
    implementation(project(":clients:client-datagokr-common"))

    testImplementation(testFixtures(project(":clients:client-datagokr-common")))

    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-context")
}
