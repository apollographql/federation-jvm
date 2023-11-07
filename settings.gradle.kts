rootProject.name = "federation-jvm"

include(":federation-graphql-java-support")
include(":federation-spring-subscription-callback")
include(":subscription-example")

project(":federation-graphql-java-support").projectDir = file("graphql-java-support")
project(":federation-spring-subscription-callback").projectDir = file("spring-subscription-callback")
