description = "GraphQL Java server support for Apollo Federation"

plugins {
    id("com.apollographql.federation.java-conventions")
    id("com.google.protobuf") version "0.9.2"
}

val annotationsVersion: String by project
val graphQLJavaVersion: String by project
val protobufVersion: String by project
val slf4jVersion: String by project
dependencies {
    compileOnly("org.jetbrains:annotations:$annotationsVersion")
    api(project(":federation-graphql-java-support-api"))
    api("com.graphql-java:graphql-java:$graphQLJavaVersion")
    api("org.slf4j:slf4j-api:$slf4jVersion")
    api("com.google.protobuf:protobuf-java:$protobufVersion")
    testCompileOnly("org.jetbrains:annotations:$annotationsVersion")
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.22.2"
    }
}
