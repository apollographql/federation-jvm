rootProject.name = "federation-jvm"

include(":federation-graphql-java-support")
include(":federation-subscription-callback")

project(":federation-graphql-java-support").projectDir = file("graphql-java-support")
project(":federation-subscription-callback").projectDir = file("subscription-callback")
