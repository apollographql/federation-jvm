import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.tasks.BintrayUploadTask

plugins {
    `build-scan`
    id("com.jfrog.bintray").version("1.8.4").apply(false)
    id("io.freefair.lombok").version("3.2.1").apply(false)
    id("org.springframework.boot").version("2.1.5.RELEASE").apply(false)
    id("org.jetbrains.dokka").version("0.9.18").apply(false)
}

allprojects {
    group = "com.apollographql.federation"
    version = "0.0.1"

    repositories {
        jcenter()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

val publicationName = "federation-support"

tasks.withType<Wrapper> {
    gradleVersion = "5.4.1"
}

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
}
