plugins {
    id("com.apollographql.federation.java-conventions")
    id("org.springframework.boot") version "3.1.3"
    id("io.spring.dependency-management") version "1.1.3"
}

val annotationsVersion: String by project
val graphQLJavaVersion: String by project
val slf4jVersion: String by project
dependencies {
    implementation(project(":federation-graphql-java-support"))
    implementation(project(":federation-spring-subscription-callback"))
    compileOnly("org.jetbrains:annotations:$annotationsVersion")
    api("com.graphql-java:graphql-java:$graphQLJavaVersion")
    api("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.springframework.boot", "spring-boot-starter-actuator")
    implementation("org.springframework.boot", "spring-boot-starter-graphql")
    implementation("org.springframework.boot", "spring-boot-starter-web")
    implementation("org.springframework.boot", "spring-boot-starter-webflux")
//    implementation("org.springframework.boot", "spring-boot-starter-websocket")
    testCompileOnly("org.jetbrains:annotations:$annotationsVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
