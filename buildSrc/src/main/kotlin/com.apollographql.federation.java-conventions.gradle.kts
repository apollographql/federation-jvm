plugins {
    jacoco
    `java-library`
    `maven-publish`
    signing

    // external plugin versions are specified in the buildSrc dependencies
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
    mavenLocal {
        content {
            includeGroup("com.apollographql.federation")
        }
    }
}

val junitVersion: String by project
dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter:$junitVersion")
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11

    if (!version.toString().endsWith("SNAPSHOT")) {
        withJavadocJar()
        withSourcesJar()
    }
}

publishing {
    publications {
        withType<MavenPublication> {
            pom {
                name.set("${project.group}:${project.name}")
                url.set("https://github.com/apollographql/federation-jvm")
                inceptionYear.set("2019")
                licenses {
                    license {
                        name.set("MIT License")
                        url.set("http://www.opensource.org/licenses/mit-license.php")
                        distribution.set("repo")
                    }
                }
                organization {
                    name.set("Apollo Graph Inc.")
                    name.set("https://www.apollographql.com/about-us")
                }
                developers {
                    developer {
                        id.set("apollographql.com")
                        name.set("The Apollo Contributors")
                        url.set("https://www.apollographql.com/careers/team")
                        organization.set("Apollo Graph Inc.")
                        organizationUrl.set("https://www.apollographql.com/about-us")
                    }
                }
                scm {
                    connection.set("scm:git:git://github.com/apollographql/federation-jvm.git")
                    developerConnection.set("scm:git:ssh://git@github.com/apollographql/federation-jvm.git")
                    tag.set("HEAD")
                    url.set("https://github.com/apollographql/federation-jvm")
                }

                // child projects need to be evaluated before their description can be read
                val mavenPom = this
                afterEvaluate {
                    mavenPom.description.set(project.description)
                }
            }
        }
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

spotless {
    java {
        importOrder()
        removeUnusedImports()
        googleJavaFormat("1.17.0")
        // exclude generated proto
        targetExclude("build/generated/**/*.java")
    }
}

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    withType<Javadoc>().configureEach {
        (options as StandardJavadocDocletOptions).let { docletOptions ->
            docletOptions.noTimestamp(true)
            docletOptions.addBooleanOption("Xdoclint:all,-missing", true)
        }
    }

    check {
        dependsOn(jacocoTestCoverageVerification)
    }
    signing {
        setRequired {
            !version.toString().endsWith("SNAPSHOT") && gradle.taskGraph.hasTask("publish")
        }
        val signingKey: String? = System.getenv("SONATYPE_GPG_KEY")
        val signingPassword: String? = System.getenv("SONATYPE_GPG_KEY_PASSWORD")
        useInMemoryPgpKeys(signingKey, signingPassword)
        sign(publishing.publications)
    }
    test {
        useJUnitPlatform()
        finalizedBy(jacocoTestReport)
    }
}
