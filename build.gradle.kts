plugins {
    id("io.github.gradle-nexus.publish-plugin") version "1.1.0"
}

tasks {
    nexusPublishing {
        repositories {
            sonatype {
                username.set(System.getenv("SONATYPE_USERNAME"))
                password.set(System.getenv("SONATYPE_PASSWORD"))
            }
        }

        transitionCheckOptions {
            maxRetries.set(60)
            delayBetween.set(java.time.Duration.ofMillis(5000))
        }
    }
}
