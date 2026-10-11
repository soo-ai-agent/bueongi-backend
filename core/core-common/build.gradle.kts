dependencies {
    api(project(":core:core-enum"))
    api(project(":storage:db-safety-spot"))
    api(project(":storage:db-share"))
    implementation(project(":support:logging"))
    implementation("org.springframework.boot:spring-boot-starter-web")
}
