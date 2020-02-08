import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

group = "io.gqljf"
version = "0.1.5"

plugins {
    `java-library`
    `maven-publish`
    idea
    id("com.google.protobuf")
    id("com.jfrog.bintray")
}

val graphqlJavaVersion: String by project
val protobufVersion: String by project
val junitVersion: String by project
val hamcrestVersion: String by project

repositories {
    jcenter()
}

dependencies {
    implementation("com.graphql-java:graphql-java:$graphqlJavaVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")

    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
    testImplementation("org.hamcrest:hamcrest:$hamcrestVersion")
}


protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:$protobufVersion"
    }
}

tasks {
    test {
        useJUnitPlatform()
    }

    jar {
        manifest {
            attributes(
                mapOf(
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version
                )
            )
        }
    }
}

val sourcesJar by tasks.registering(Jar::class) {
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

val projectUrl = "https://github.com/rkudryashov/graphql-java-federation.git"

publishing {
    publications {
        create<MavenPublication>("lib") {
            groupId = project.group.toString()
            artifactId = project.name
            // todo setup plugin
            version = project.version.toString()
            from(components["java"])
            artifact(sourcesJar.get())

            pom.withXml {
                asNode().apply {
                    appendNode(
                        "description",
                        "The library provides an ability to graphql-java service to work as Apollo Server downstream service implementing Apollo Federation spec"
                    )
                    appendNode("name", rootProject.name)
                    appendNode("licenses").appendNode("license").apply {
                        appendNode("name", "MIT")
                    }
                    appendNode("scm").apply {
                        appendNode("url", projectUrl)
                    }
                }
            }
        }
    }
}

bintray {
    user = System.getProperty("bintrayUser")
    key = System.getProperty("bintrayKey")
    publish = true
    setPublications("lib")
    pkg.apply {
        userOrg = "gqljf"
        repo = "maven"
        name = "graphql-java-federation"
        setLicenses("MIT")
        vcsUrl = projectUrl
        githubRepo = githubRepo
        version.apply {
            name = project.version.toString()
        }
    }
}

idea {
    module {
        sourceDirs.add(file("${projectDir}/src/generated/main/java"))
    }
}
