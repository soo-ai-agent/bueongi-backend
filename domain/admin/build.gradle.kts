dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-common"))
    implementation(project(":storage:db-core"))
    implementation(project(":support:monitoring"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(project(":domain:facility"))
    implementation(project(":domain:share"))
    implementation("org.springframework.boot:spring-boot-starter-security")
    testImplementation("io.jsonwebtoken:jjwt-api:0.12.6")
    testRuntimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    testRuntimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
}
