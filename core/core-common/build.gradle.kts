dependencies {
    api(project(":core:core-enum"))
    api(project(":storage:db-core"))
    implementation(project(":support:logging"))
    implementation("org.springframework.boot:spring-boot-starter-web")
}
