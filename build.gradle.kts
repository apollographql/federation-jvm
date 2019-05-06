plugins {
    `build-scan`
    id("io.freefair.lombok").version("3.2.1").apply(false)
    id("org.springframework.boot").version("2.1.5.RELEASE").apply(false)
}

allprojects {
    group = "com.apollographql.federation"

    repositories {
        jcenter()
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "5.4.1"
}

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
}
