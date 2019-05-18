plugins {
    `java`
    id("io.spring.dependency-management")
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":libs:graphql-java-support"))
    implementation("com.graphql-java-kickstart:graphql-spring-boot-starter:5.8.1")
    implementation("com.graphql-java-kickstart:graphiql-spring-boot-starter:5.8.1")
    implementation("org.springframework.boot:spring-boot-dependencies:2.1.5.RELEASE")
    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("com.graphql-java-kickstart:graphql-spring-boot-starter-test:5.8.1")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
