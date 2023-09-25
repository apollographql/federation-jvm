description = "GraphQL Java server support for Apollo Federation"

plugins {
    id("com.apollographql.federation.java-conventions")
    id("org.springframework.boot") version "3.1.3"
    id("io.spring.dependency-management") version "1.1.3"
}

val annotationsVersion: String by project
val graphQLJavaVersion: String by project
val slf4jVersion: String by project
dependencies {
    compileOnly("org.jetbrains:annotations:$annotationsVersion")
    api("com.graphql-java:graphql-java:$graphQLJavaVersion")
    api("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.springframework.boot", "spring-boot-starter-actuator")
    implementation("org.springframework.boot", "spring-boot-starter-graphql")
    implementation("org.springframework.boot", "spring-boot-starter-web")
    implementation("org.springframework.boot", "spring-boot-starter-webflux")
    testCompileOnly("org.jetbrains:annotations:$annotationsVersion")
}
