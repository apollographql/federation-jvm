plugins {
    id("org.springframework.boot") version "3.0.0"
    id("io.spring.dependency-management") version "1.1.0"
    java
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.apollographql.federation", "federation-graphql-java-support")
    implementation("org.jetbrains", "annotations", "23.0.0")
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
