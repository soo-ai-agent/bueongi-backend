dependencies {
    implementation(project(":core:core-enum"))
    implementation(project(":core:core-common"))
    implementation(project(":clients:client-kakao"))
    implementation(project(":storage:db-core"))
    implementation(project(":support:logging"))

    implementation("org.springframework.boot:spring-boot-starter-web")
}
