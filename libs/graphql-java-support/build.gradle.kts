plugins {
    `java-library`
    id("io.freefair.lombok")
}

dependencies {
    api("com.graphql-java:graphql-java:12.0")
    compileOnly("org.jetbrains:annotations:17.0.0")
    testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}
