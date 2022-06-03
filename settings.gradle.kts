rootProject.name = "federation-parent"

include(":federation-graphql-java-support")
include(":federation-graphql-java-support-api")

project(":federation-graphql-java-support").projectDir = file("graphql-java-support")
project(":federation-graphql-java-support-api").projectDir = file("graphql-java-support-api")
