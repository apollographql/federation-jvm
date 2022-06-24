import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

description = "GraphQL Java server support for Apollo Federation"

plugins {
    id("com.apollographql.federation.java-conventions")
    id("com.google.protobuf") version "0.8.18"
}

val annotationsVersion: String by project
val graphQLJavaVersion: String by project
val junitVersion: String by project
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
        artifact = "com.google.protobuf:protoc:3.21.2"
    }
}

// gradle protobuf plugin currently does not correctly register the sources
// this is a workaround for intellij to correctly recognize the generated sources
// https://github.com/google/protobuf-gradle-plugin/issues/109
sourceSets {
    val main by getting { }
    main.java.srcDirs("build/generated/source/proto/main/java")
}
