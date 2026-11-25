plugins {
    id("java-test-fixtures")
}

dependencies {
    implementation(project(":core:core-enum"))
    implementation("org.springframework:spring-context")
    implementation("org.slf4j:slf4j-api")
    implementation("jakarta.annotation:jakarta.annotation-api")

    testImplementation("org.assertj:assertj-core")
}
