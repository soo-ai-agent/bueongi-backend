plugins {
    id("java-test-fixtures")
}

dependencies {
    implementation(project(":support:logging"))

    implementation("org.springframework:spring-web")
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")

    testFixturesImplementation("org.springframework:spring-test")
    testFixturesImplementation("org.springframework:spring-web")
}
