description = "Integration APIs for federation-graphql-java-support"

plugins {
    id("com.apollographql.federation.java-conventions")
}

val annotationsVersion: String by project
dependencies {
    compileOnly("org.jetbrains:annotations:$annotationsVersion")
}
