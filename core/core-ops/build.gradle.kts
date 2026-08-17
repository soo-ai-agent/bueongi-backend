dependencies {
    implementation(project(":core:core-common"))
    implementation(project(":core:core-enum"))
    implementation(project(":storage:db-core"))
    implementation(project(":support:monitoring"))
    implementation(project(":clients:client-slack"))
    implementation(project(":support:util"))

    implementation("org.springframework.boot:spring-boot-starter-web")
}
