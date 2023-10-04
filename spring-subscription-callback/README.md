# Subscription HTTP Callback Support for Spring GraphQL

GraphQL subscriptions enable clients to receive continual, real-time updates whenever new data becomes available. Unlike
queries and mutations, subscriptions are long-lasting. This means a client can receive multiple updates from a single subscription.

[Spring GraphQL](https://docs.spring.io/spring-graphql/reference/) provides out of box support for GraphQL subscriptions
over WebSockets using [graphql-transport-ws](https://github.com/enisdenjo/graphql-ws) protocol. This library adds support
for subscriptions using [Apollo HTTP callback protocol](https://www.apollographql.com/docs/router/executing-operations/subscription-callback-protocol).

See [Apollo Router](https://www.apollographql.com/docs/router/executing-operations/subscription-support) for additional
details about Federation and Subscription support.

```mermaid
sequenceDiagram;
  Router->>Spring GraphQL Server: Register subscription and callback URL over HTTP
  Note over Spring GraphQL Server: Initialize protocol
  Spring GraphQL Server->>Router: check
  Router->>Spring GraphQL Server: HTTP 204 No Content
  Note over Spring GraphQL Server: Start subsciption and heartbeat<br>on background threads
  Spring GraphQL Server->>Router: HTTP 200 OK<br>{ "data": null }
  Note over Router: Time passes
  par Subscription to Router
      Note over Subscription: While data available
      loop Data Available
          Subscription->>Router: next<br>{ "data": <subscriptionData> }
          Router->>Subscription: HTTP 200 OK
      end
      Note over Subscription: Subscription Complete/Error
      Subscription->>Router: complete
      Router->>Subscription: HTTP 202 Accepted
  and Heartbeat to Router
      loop Emit every 5 seconds
          Heartbeat->>Router: check
          Router->>Heartbeat: HTTP 204 No Content
      end
  end
```

## Installation

Federation JVM libraries are published to [Maven Central](https://central.sonatype.com/artifact/com.apollographql.federation/federation-spring-subscription-callback).
Using a JVM dependency manager, link `federation-spring-subscription-callback` to your project.

With Maven:

```xml
<dependency>
  <groupId>com.apollographql.federation</groupId>
  <artifactId>federation-spring-subscription-callback</artifactId>
  <version>${latestVersion}</version>
</dependency>
```

With Gradle (Kotlin):

```groovy
implementation("com.apollographql.federation:federation-spring-subscription-callback:$latestVersion")
```

## Usage

In order to enable HTTP subscription callback protocol support you need to configure `CallbackGraphQlHttpHandler` bean in your
application context.

This library provides support for both WebMVC and WebFlux applications so make sure to instantiate correct flavor of protocol.

* `com.apollographql.subscription.webmvc.CallbackGraphQlHttpHandler` for WebMVC applications
* `com.apollographql.subscription.webflux.CallbackGraphQlHttpHandler` for WebFlux applications

Given a subscription

```java
@Controller
public class MySubscriptionController {

    @SubscriptionMapping
    public Flux<Integer> counter() {
        return Flux.just(1, 2, 3, 4, 5, 6)
                .delayElements(Duration.ofMillis(200));
    }
}
```

We can enable subscription HTTP callback support using following configuration

```java
@Configuration
public class GraphQLConfiguration {

    @Bean
    public GraphQlHttpHandler graphQlHttpHandler(WebGraphQlHandler webGraphQlHandler) {
        return new CallbackGraphQlHttpHandler(webGraphQlHandler);
    }

    // regular federation transform
    @Bean
    public GraphQlSourceBuilderCustomizer federationTransform() {
        DataFetcher<?> entityDataFetcher =
                env -> {
                    List<Map<String, Object>> representations = env.getArgument(_Entity.argumentName);
                    return representations.stream()
                            .map(
                                    representation -> {
                                        // TODO implement entity data fetcher logic here
                                        return null;
                                    })
                            .collect(Collectors.toList());
                };

        return builder ->
                builder.schemaFactory(
                        (registry, wiring) ->
                                Federation.transform(registry, wiring)
                                        .fetchEntities(entityDataFetcher)
                                        .resolveEntityType(new ClassNameTypeResolver())
                                        .build());
    }
}
```
