import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.tasks.BintrayUploadTask
import org.jetbrains.dokka.gradle.DokkaTask

plugins {
    id("com.jfrog.bintray")
    id("org.jetbrains.dokka")
}

subprojects {
    apply(plugin = "com.jfrog.bintray")
    apply(plugin = "java")
    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.dokka")

    val sourceSets = project.the<SourceSetContainer>()

    val sourcesJar by tasks.creating(Jar::class) {
        from(sourceSets["main"].allSource)
        archiveClassifier.set("sources")
    }

    val doc by tasks.creating(DokkaTask::class) {
        outputFormat = "html"
        outputDirectory = "$buildDir/javadoc"
    }

    val javadocJar by tasks.creating(Jar::class) {
        dependsOn(doc)
        from(doc)
        archiveClassifier.set("javadoc")
    }

    configure<PublishingExtension> {
        publications.create<MavenPublication>("published") {
            from(components["java"])
            artifact(sourcesJar)
            artifact(javadocJar)

            groupId = this@subprojects.group as? String
            artifactId = this@subprojects.name
            version = this@subprojects.version as? String

            pom.withXml {
                asNode().apply {
                    appendNode("url", "https://github.com/apollographql/federation-jvm")
                }
            }
        }
    }

    bintray {
        user = System.getenv("BINTRAY_USER")
        key = System.getenv("BINTRAY_KEY")
        pkg(delegateClosureOf<BintrayExtension.PackageConfig> {
            userOrg = "apollographql"
            repo = "maven"
            name = "federation-support"

            description = "Apollo Federation on the JVM"
            desc = description
            githubRepo = "apollographql/federation-jvm"
            vcsUrl = "https://github.com/apollographql/federation-jvm"
            setLabels("graphql")
            setLicenses("MIT")

            setPublications("published")

            version(delegateClosureOf<BintrayExtension.VersionConfig> {
                gpg(delegateClosureOf<BintrayExtension.GpgConfig> {
                    sign = false
                })
            })
        })
    }
}

tasks.withType<BintrayUploadTask> {
    enabled = false
}
