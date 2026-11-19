description = "GraphQL Java server support for Apollo Federation"

plugins {
    id("com.apollographql.federation.java-conventions")
}

val annotationsVersion = property("annotationsVersion") as String
val graphQLJavaVersion = property("graphQLJavaVersion") as String
val mockWebServerVersion = property("mockWebServerVersion") as String
val slf4jVersion = property("slf4jVersion") as String
val springBootVersion = property("springBootVersion") as String
val springGraphQLVersion = property("springGraphQLVersion") as String
val reactorVersion = property("reactorVersion") as String
dependencies {
    compileOnly("org.jetbrains:annotations:$annotationsVersion")
    implementation("com.graphql-java:graphql-java:$graphQLJavaVersion")
    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("org.springframework.boot:spring-boot-starter-graphql:$springBootVersion") {
        exclude(group = "com.graphql-java", module = "graphql-java")
    }
    implementation("org.springframework.boot:spring-boot-starter-web:$springBootVersion")
    implementation("org.springframework.boot:spring-boot-starter-webflux:$springBootVersion")
    testCompileOnly("org.jetbrains:annotations:$annotationsVersion")
    testImplementation(project(":federation-graphql-java-support"))
    testImplementation("com.squareup.okhttp3:mockwebserver:$mockWebServerVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-websocket:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-webclient-test:$springBootVersion")
    testImplementation("org.springframework.graphql:spring-graphql-test:$springGraphQLVersion")
    testImplementation("io.projectreactor:reactor-test:$reactorVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
