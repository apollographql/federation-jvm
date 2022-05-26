rootProject.name = "federation-parent"

include(":federation-graphql-java-support")
include(":federation-spring-example")
include(":federation-graphql-java-support-api")

project(":federation-graphql-java-support").projectDir = file("graphql-java-support")
project(":federation-spring-example").projectDir = file("spring-example")
project(":federation-graphql-java-support-api").projectDir = file("graphql-java-support-api")
