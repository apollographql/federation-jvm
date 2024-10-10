plugins {
    id("io.github.gradle-nexus.publish-plugin") version "2.0.0"
}

tasks {
    nexusPublishing {
        repositories {
            sonatype {
                nexusUrl.set(uri("https://s01.oss.sonatype.org/service/local/"))
                username.set(System.getenv("SONATYPE_NEXUS_USERNAME"))
                password.set(System.getenv("SONATYPE_NEXUS_PASSWORD"))
                stagingProfileId.set(System.getenv("COM_APOLLOGRAPHQL_PROFILE_ID"))
            }
        }

        transitionCheckOptions {
            maxRetries.set(60)
            delayBetween.set(java.time.Duration.ofMillis(5000))
        }
    }
}
