import java.util.Properties

plugins {
    id("org.springframework.boot") version "3.2.2"
    id("io.spring.dependency-management") version "1.1.4"
    java
}

repositories {
    mavenCentral()
}

val properties = Properties()
properties.load(File(rootDir.parent, "gradle.properties").inputStream())
for ((key, value) in properties) {
    project.ext[key.toString()] = value
}

val annotationsVersion: String by project
dependencies {
    implementation("com.apollographql.federation", "federation-graphql-java-support")
    implementation("org.jetbrains", "annotations", annotationsVersion)
    implementation("org.springframework.boot", "spring-boot-starter-actuator")
    implementation("org.springframework.boot", "spring-boot-starter-graphql")
    implementation("org.springframework.boot", "spring-boot-starter-web")
    testImplementation("org.springframework.boot", "spring-boot-starter-test")
    testImplementation("org.springframework.boot", "spring-boot-starter-webflux")
    testImplementation("org.springframework.graphql", "spring-graphql-test")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<Test> {
    useJUnitPlatform()
}
