dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-common"))
    implementation(project(":storage:db-core"))
    implementation(project(":support:monitoring"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation(project(":domain:facility"))
    implementation(project(":clients:client-tmap"))
}
