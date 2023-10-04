description = "GraphQL Java server support for Apollo Federation"

plugins {
    id("com.apollographql.federation.java-conventions")
}

val annotationsVersion: String by project
val graphQLJavaVersion: String by project
val slf4jVersion: String by project
val springBootVersion: String by project
val springGraphQLVersion: String by project
dependencies {
    compileOnly("org.jetbrains", "annotations", annotationsVersion)
    api("com.graphql-java", "graphql-java", graphQLJavaVersion)
    api("org.slf4j", "slf4j-api", slf4jVersion)
    implementation("org.springframework.boot", "spring-boot-starter-graphql", springBootVersion)
    implementation("org.springframework.boot", "spring-boot-starter-web", springBootVersion)
    implementation("org.springframework.boot", "spring-boot-starter-webflux", springBootVersion)
    testCompileOnly("org.jetbrains:annotations:$annotationsVersion")
    testImplementation(project(":federation-graphql-java-support"))
    testImplementation("com.squareup.okhttp3:mockwebserver:4.11.0")
    testImplementation("org.springframework.boot", "spring-boot-starter-test", springBootVersion)
    testImplementation("org.springframework.boot", "spring-boot-starter-websocket", springBootVersion)
    testImplementation("org.springframework.graphql", "spring-graphql-test", springGraphQLVersion)
    testImplementation("io.projectreactor:reactor-test:3.5.9")

}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
