[![MIT License](https://img.shields.io/github/license/apollographql/federation-jvm.svg)](LICENSE)
[![Download](https://api.bintray.com/packages/apollographql/maven/federation-jvm/images/download.svg)](https://bintray.com/apollographql/maven/federation-jvm/_latestVersion)
[![CircleCI](https://circleci.com/gh/apollographql/federation-jvm.svg?style=svg)](https://circleci.com/gh/apollographql/federation-jvm)

# Apollo Federation on the JVM

Packages published to [our bintray repository](https://bintray.com/apollographql/maven/federation-jvm)
and available [in jcenter](https://jcenter.bintray.com/com/apollographql/federation/);
release notes in [RELEASE_NOTES.md](RELEASE_NOTES.md).

An example of [graphql-spring-boot](https://www.graphql-java-kickstart.com/spring-boot/) microservice is available in [spring-example](spring-example).

## Getting started

### Dependency management with Gradle

Make sure JCenter is among your repositories:

```groovy
repositories {
    jcenter()
}
```

Add a dependency to `graphql-java-support`:

```groovy
dependencies {
    implementation 'com.apollographql.federation:federation-graphql-java-support:0.3.3'
}
```

### graphql-java schema transformation

`graphql-java-support` produces a `graphql.schema.GraphQLSchema` by transforming your existing schema in accordance to the
[federation specification](https://www.apollographql.com/docs/apollo-server/federation/federation-spec/).
It follows the `Builder` pattern.

Start with `com.apollographql.federation.graphqljava.Federation.transform(…)`, which can receive either:
- A `GraphQLSchema`;
- A `TypeDefinitionRegistry`, optionally with a `RuntimeWiring`;
- A String, Reader, or File declaring the schema using the [Schema Definition Language](https://www.apollographql.com/docs/apollo-server/essentials/schema/#schema-definition-language),
  optionally with a `RuntimeWiring`;

and returns a `SchemaTransformer`.

If your schema does not contain any types annotated with the `@key` directive, nothing else is required.
You can build a transformed `GraphQLSchema` with `SchemaTransformer#build()`, and confirm it exposes `query { _schema { sdl } }`.

Otherwise, all types annotated with `@key` will be part of the `_Entity` union type,
and reachable through `query { _entities(representations: [Any!]!) { … } }`. Before calling `SchemaTransformer#build()`,
you will also need to provide:
- A `TypeResolver` for `_Entity` using `SchemaTransformer#resolveEntityType(TypeResolver)`;
- A `DataFetcher` or `DataFetcherFactory` for `_entities`
  using `SchemaTransformer#fetchEntities(DataFetcher|DataFetcherFactory)`.

A minimal but complete example is available in
[InventorySchemaProvider](spring-example/src/main/java/com/apollographql/federation/springexample/InventorySchemaProvider.java).

### Federated tracing

To make your server generate performance traces and return them along with
responses to the Apollo Gateway (which then can send them to Apollo Graph
Manager), install the `FederatedTracingInstrumentation` into your `GraphQL`
object:

```java
GraphQL graphql = GraphQL.newGraphQL(graphQLSchema)
  .instrumentation(new FederatedTracingInstrumentation())
  .build()
```

It is generally desired to only create traces for requests that actually come
from Apollo Gateway, as they aren't helpful if you're connecting directly to
your backend service for testing. In order for `FederatedTracingInstrumentation`
to know if the request is coming from Gateway, you need to give it access to the
HTTP request's headers, by making the `context` part of your `ExecutionInput`
implement the `HTTPRequestHeaders` interface.  For example:

```java
    HTTPRequestHeaders context = new HTTPRequestHeaders() {
        @Override
        public @Nullable String getHTTPRequestHeader(String caseInsensitiveHeaderName) {
            return myIncomingHTTPRequest.getHeader(caseInsensitiveHeaderName);
        }
    }
    graphql.execute(ExecutionInput.newExecutionInput(queryString).context(context));

```
