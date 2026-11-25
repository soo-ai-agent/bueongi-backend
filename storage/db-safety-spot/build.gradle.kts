plugins {
    id("java-test-fixtures")
}

dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":support:util"))
    implementation("org.springframework:spring-context")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    implementation("org.slf4j:slf4j-api")
    implementation("jakarta.annotation:jakarta.annotation-api")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    testFixturesImplementation(project(":support:util"))
    testImplementation("org.assertj:assertj-core")
    testImplementation("org.postgresql:postgresql")
    testImplementation("org.flywaydb:flyway-database-postgresql")
}
