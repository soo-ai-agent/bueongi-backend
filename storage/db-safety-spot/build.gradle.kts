plugins {
    id("java-test-fixtures")
}

dependencies {
    implementation(project(":core:core-enum"))

    testImplementation("org.assertj:assertj-core")
}
