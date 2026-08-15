plugins {
    id("java-test-fixtures")
}

dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-common"))
    implementation(project(":storage:db-core"))
    implementation(project(":support:util"))

    implementation("org.springframework.boot:spring-boot-starter-web")
}
