[![Continuous Integration](https://github.com/apollographql/federation-jvm/workflows/Continuous%20Integration/badge.svg)](https://github.com/apollographql/federation-jvm/actions?query=workflow%3A%22Continuous+Integration%22)
[![MIT License](https://img.shields.io/github/license/apollographql/federation-jvm.svg)](LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.apollographql.federation/federation-graphql-java-support.svg)](https://maven-badges.herokuapp.com/maven-central/com.apollographql.federation/federation-graphql-java-support)
[![Join the community forum](https://img.shields.io/badge/join%20the%20community-forum-blueviolet)](https://community.apollographql.com)

# Apollo Federation on the JVM

[**Apollo Federation**](https://www.apollographql.com/docs/federation/) is a powerful, open architecture that helps you create a **unified supergraph** that combines multiple GraphQL APIs.
`graphql-java-support` provides Apollo Federation support for building subgraphs in the `graphql-java` ecosystem. Individual subgraphs can be run independently of each other but can also specify
relationships to the other subgraphs by using Federated directives. See [Apollo Federation documentation](https://www.apollographql.com/docs/federation/) for details.

```mermaid
graph BT;
  gateway([Supergraph<br/>gateway]);
  serviceA[Users<br/>subgraph];
  serviceB[Products<br/>subgraph];
  serviceC[Reviews<br/>subgraph];
  gateway --- serviceA & serviceB & serviceC;
```

`graphql-java-support` is built on top of `graphql-java` and provides transformation logic to make your GraphQL schemas Federation compatible. `SchemaTransformer` adds common Federation
type definitions (e.g. `_Any` scalar, `_Entity` union, Federation directives, etc) and allows you to easily specify your Federated entity resolvers.

This project also provides a set of Federation aware instrumentations:

* `CacheControlInstrumentation` - instrumentation that computes a max age for an operation based on `@cacheControl` directives
* `FederatedTracingInstrumentation` - instrumentation that generates trace information for federated operations

## Installation

Federation JVM libraries are published to [Maven Central](https://search.maven.org/search?q=g:com.apollographql.federation%20AND%20a:federation-graphql-java-support).
Using a JVM dependency manager, link `graphql-java-support` to your project.

With Maven:

```xml
<dependency>
  <groupId>com.apollographql.federation</groupId>
  <artifactId>federation-graphql-java-support</artifactId>
  <version>${latestVersion}</version>
</dependency>
```

With Gradle (Groovy):

```groovy
implementation 'com.apollographql.federation:federation-graphql-java-support:$latestVersion'
```

## Usage

Additional documentation on the Apollo Federation and JVM usage can be found on the [Apollo Documentation Portal](https://www.apollographql.com/docs/federation/).

Federation JVM example integrations

* [Spring GraphQL Federation Example](https://github.com/apollographql/federation-jvm-spring-example)
* [Netflix DGS Federation Example](https://github.com/Netflix/dgs-federation-example)
* [GraphQL Java Kickstart Federation Example](https://github.com/setchy/graphql-java-kickstart-federation-example)

### Creating Federated Schemas

Using `graphql-java` (or [your](https://docs.spring.io/spring-graphql/docs/current/reference/html/) [framework](https://netflix.github.io/dgs/) of [choice](https://www.graphql-java-kickstart.com/spring-boot/))
we first need to create a GraphQL schema.

Assuming there is already a subgraph that defines a base `Product` type

```graphql
# product subgraph
type Query {
  product(id: ID!): Product
}

type Product @key(fields: "id") {
  id: ID!,
  description: String
}
```

We can create another subgraph that extends `Product` type and adds the `reviews` field.

```graphql
# reviews subgraph
type Product @extends @key(fields: "id") {
    id: ID! @external
    reviews: [Review!]!
}

type Review {
    id: ID!
    text: String
    rating: Int!
}
```

>NOTE: This subgraph does not specify any top level queries.

Using the above schema file, we first need to generate the `TypeDefinitionRegistry` and `RuntimeWiring` objects.

```java
SchemaParser parser = new SchemaParser();
TypeDefinitionRegistry typeDefinitionRegistry = parser.parse(Paths.get("schema.graphqls").toFile());
RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring().build();
```

We can then generate Federation compatible schema using schema transformer. In order to be able to resolve the federated `Product` type, we need to provide `TypeResolver` to resolve [`_Entity`](https://www.apollographql.com/docs/federation/federation-spec/#union-_entity)
union type and a `DataFetcher` to resolve [`_entities`](https://www.apollographql.com/docs/federation/federation-spec/#query_entities) query.

```java
DataFetcher entityDataFetcher = env -> {
    List<Map<String, Object>> representations = env.getArgument(_Entity.argumentName);
    return representations.stream()
        .map(representation -> {
            if ("Product".equals(representation.get("__typename"))) {
                return new Product((String)representation.get("id"));
            }
            return null;
        })
        .collect(Collectors.toList());
    };
TypeResolver entityTypeResolver = env -> {
    final Object src = env.getObject();
    if (src instanceof Product) {
        return env.getSchema()
            .getObjectType("Product");
    }
    return null;
};

GraphQLSchema federatedSchema = Federation.transform(typeDefinitionRegistry, runtimeWiring)
    .fetchEntities(entityDataFetcher)
    .resolveEntityType(entityTypeResolver)
    .build();
```

This will generate a schema with additional federated info.

```graphql
union _Entity = Product

type Product @extends @key(fields : "id") {
  id: ID! @external
  reviews: [Review!]!
}

type Query {
  _entities(representations: [_Any!]!): [_Entity]!
  _service: _Service
}

type Review {
  id: ID!
  rating: Int!
  text: String
}

type _Service {
  sdl: String!
}

scalar _Any

scalar _FieldSet
```

### Instrumentation

#### Federated Tracing

[Tracing your GraphQL queries](https://www.apollographql.com/docs/federation/metrics) can provide you detailed insights into your GraphQL layer's performance and usage. Single federated query may
be executed against multiple GraphQL servers. Apollo Gateway provides ability to aggregate trace data generated by the subgraphs calls and then send them to [Apollo Studio](https://www.apollographql.com/docs/studio/)

To make your server generate performance traces and return them along with responses to the Apollo Gateway, install the `FederatedTracingInstrumentation` into your `GraphQL` object:

```java
GraphQL graphql = GraphQL.newGraphQL(graphQLSchema)
        .instrumentation(new FederatedTracingInstrumentation())
        .build();
```

**By default, all requests will be traced.** In order to skip dev requests and only trace requests that come from the Apollo Gateway, you should populate tracing information in the `GraphQLContext` map.
This will ensure that only requests with `apollo-federation-include-trace=ftv1` header value will be traced.

```java
String federatedTracingHeaderValue = httpRequest.getHeader(FEDERATED_TRACING_HEADER_NAME);

Map<Object, Object> contextMap = new HashMap<>();
contextMap.put(FEDERATED_TRACING_HEADER_NAME, federatedTracingHeaderValue);

ExecutionInput executionInput = ExecutionInput.newExecutionInput()
        .graphQLContext(contextMap)
        .query(queryString)
        .build();
graphql.executeAsync(executionInput);
```

## Contact

If you have a specific question about the library or code, please start a discussion in the [Apollo community forums](https://community.apollographql.com/).

## Contributing

To get started, please fork the repo and checkout a new branch. You can then build the library locally with Gradle

```shell
./gradlew clean build
```

See more info in [CONTRIBUTING.md](CONTRIBUTING.md).

After you have your local branch set up, take a look at our open issues to see where you can contribute.

## Security

For more info on how to contact the team for security issues, see our [Security Policy](https://github.com/apollographql/federation-jvm/security/policy).

## License

This library is licensed under [The MIT License (MIT)](LICENSE).
