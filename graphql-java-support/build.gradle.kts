description = "GraphQL Java server support for Apollo Federation"

plugins {
    id("com.apollographql.federation.java-conventions")
    id("com.google.protobuf") version "0.10.1"
}

val annotationsVersion = property("annotationsVersion") as String
val graphQLJavaVersion = property("graphQLJavaVersion") as String
val protobufVersion = property("protobufVersion") as String
val slf4jVersion = property("slf4jVersion") as String
val jmhVersion = property("jmhVersion") as String
dependencies {
    compileOnly("org.jetbrains:annotations:$annotationsVersion")
    api("com.graphql-java:graphql-java:$graphQLJavaVersion")
    api("org.slf4j:slf4j-api:$slf4jVersion")
    api("com.google.protobuf:protobuf-java:$protobufVersion")
    testCompileOnly("org.jetbrains:annotations:$annotationsVersion")
    testImplementation("org.openjdk.jmh:jmh-core:$jmhVersion")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:$jmhVersion")
}

tasks.register<JavaExec>("jmh") {
    description = "Runs the JMH benchmarks in the test source set."
    group = "verification"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    // Pass extra JMH arguments with -PjmhArgs="...", e.g. -PjmhArgs="-prof gc".
    val jmhArgs = (project.findProperty("jmhArgs") as String?) ?: ""
    args(jmhArgs.split(" ").filter { it.isNotBlank() })
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
}
