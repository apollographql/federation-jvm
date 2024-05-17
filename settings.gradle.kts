rootProject.name = "federation-jvm"

include(":federation-graphql-java-support")
// TODO: disabling spring-subscription-callback module until Spring Boot 3.3 is released
//include(":federation-spring-subscription-callback")

project(":federation-graphql-java-support").projectDir = file("graphql-java-support")
// TODO: disabling spring-subscription-callback module until Spring Boot 3.3 is released
//project(":federation-spring-subscription-callback").projectDir = file("spring-subscription-callback")
