[![MIT License](https://img.shields.io/github/license/rkudryashov/graphql-java-federation.svg)](LICENSE)
[![Download](https://api.bintray.com/packages/gqljf/maven/graphql-java-federation/images/download.svg)](https://bintray.com/gqljf/maven/graphql-java-federation/_latestVersion)
[![TravisCI](https://travis-ci.com/rkudryashov/graphql-java-federation.svg)](https://travis-ci.com/rkudryashov/graphql-java-federation)

# graphql-java federation

Library to adapt graphql-java services to Apollo Federation spec.

Required JDK version: 11+

### Dependency management with Gradle

Make sure JCenter is among your repositories (Gradle Kotlin DSL is shown):

```kotlin
repositories {
    jcenter()
}
```

Add a dependency to `graphql-java-federation`:

```kotlin
dependencies {
    implementation("io.gqljf:graphql-java-federation:$graphqlJavaFederationVersion")
}
```

### graphql-java schema transformation

`graphql-java-federation` produces a `graphql.schema.GraphQLSchema` by transforming your existing schema in accordance to the
[federation specification](https://www.apollographql.com/docs/apollo-server/federation/federation-spec/). It follows the `Builder` pattern. Start with 
`new io.gqljf.federation.FederatedSchemaBuilder()`, then setup it:

- `schemaInputStream()`  
Required
- `runtimeWiring()`  
Required
- `excludeSubscriptionsFromApolloSdl()`  
Set `true` if your service's schema defines subscriptions. (Subscriptions don't work through Apollo Server because of the issue: https://github.com/apollographql/apollo-server/issues/3357 
(subscriptions still work in a standalone application))
- `federatedEntitiesResolvers()`  
If your schema does not contain any types annotated with the `@key` directive (that is distributed entities), method should not be called. Otherwise, all types annotated 
with `@key` should be part of the `_Entity` union type, and reachable through `query { _entities(representations: [Any!]!) { … } }`. To do it you also need to provide list of 
`FederatedEntityResolver` (each should be parameterized with Java types of identifier and entity and provided by entity's type name in the schema and function that returns entity by its id):  
```java
List<FederatedEntityResolver<?, ?>> entityResolvers = List.of(
        new FederatedEntityResolver<Long, LongEntityDummy>("LongEntityDummy", id -> new LongEntityDummy(id, "qwerty")) {
        }
);
```

Then you can build a transformed `GraphQLSchema` with `FederatedSchemaBuilder.build()`, and make sure it exposes `query { _schema { sdl } }`.

Full example of usage looks like:
```java
List<FederatedEntityResolver<?, ?>> entityResolvers = List.of(
        new FederatedEntityResolver<Long, LongEntityDummy>("LongEntityDummy", id -> new LongEntityDummy(id, "qwerty")) {
        }
);

GraphQLSchema transformed = new FederatedSchemaBuilder()
        .schemaInputStream(getResourceAsStream("entity-schema.graphqls"))
        .runtimeWiring(RuntimeWiring.newRuntimeWiring().build())
        .federatedEntitiesResolvers(entityResolvers)
        .build();
```

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
