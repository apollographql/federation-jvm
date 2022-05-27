description = "Spring Boot example of federation-graphql-java-support usage"

plugins {
    id("com.apollographql.federation.java-conventions")
    id("org.springframework.boot") version "2.7.0"
}

val annotationsVersion: String by project
val graphQLJavaKickstartVersion: String by project
val graphQLJavaToolsVersion: String by project
val springBootVersion: String by project
dependencies {
    compileOnly("org.jetbrains:annotations:$annotationsVersion")
    implementation(project(":federation-graphql-java-support"))
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    // override extended-scalars version
    implementation("com.graphql-java:graphql-java-extended-scalars:17.0")
    implementation("com.graphql-java-kickstart:graphql-spring-boot-starter:$graphQLJavaKickstartVersion")
    implementation("com.graphql-java-kickstart:graphql-java-tools:$graphQLJavaToolsVersion")
    implementation("com.graphql-java-kickstart:graphiql-spring-boot-starter:$graphQLJavaKickstartVersion")
    testImplementation("com.graphql-java-kickstart:graphql-spring-boot-starter-test:$graphQLJavaKickstartVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<PublishToMavenRepository>().configureEach {
    enabled = false
}
