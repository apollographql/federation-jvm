package com.apollographql.subscription;

import static com.apollographql.subscription.CallbackTestUtils.createMockGraphQLRequest;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER_VALUE;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.springframework.graphql.test.tester.WebSocketGraphQlTester;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.test.StepVerifier;

public abstract class CallbackGraphQLHttpHandlerAbstrIT {

  public void verifyQueriesWorks(WebTestClient testClient) {
    var graphQLRequest = Map.of("query", "query { hello }");
    testClient
        .post()
        .uri("/graphql")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(graphQLRequest)
        .exchange()
        .expectStatus()
        .is2xxSuccessful()
        .expectBody()
        .jsonPath("$.data.hello")
        .isEqualTo("Hello World!");
  }

  public void verifyWebSocketSubscriptionWorks(String url) {
    System.out.println("TEST URL: " + url);
    WebSocketClient client = new ReactorNettyWebSocketClient();

    WebSocketGraphQlTester tester = WebSocketGraphQlTester.builder(url, client).build();
    var result =
        tester
            .document("subscription { counter }")
            .executeSubscription()
            .toFlux("counter", Integer.class);

    StepVerifier.create(result).expectNext(1).expectNext(2).expectNext(3).verifyComplete();
  }

  public void verifyPostSubscriptionsWithoutCallbackDontWork(WebTestClient testClient) {
    var graphQLRequest = Map.of("query", "subscription { counter }");
    testClient
        .post()
        .uri("/graphql")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(graphQLRequest)
        .exchange()
        .expectStatus()
        .is4xxClientError();
  }

  public void verifySuccessfulCallbackInit(WebTestClient testClient) {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse()
              .setResponseCode(HttpStatus.NO_CONTENT.value())
              .setHeader(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE));
      server.enqueue(new MockResponse().setResponseCode(HttpStatus.NO_CONTENT.value()));
      server.enqueue(new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value()));

      // Start the server.
      server.start();

      var subscriptionId = UUID.randomUUID().toString();
      var callbackUrl = server.url("/callback/" + subscriptionId);

      var graphQLRequest = createMockGraphQLRequest(subscriptionId, callbackUrl.toString());
      testClient
          .post()
          .uri("/graphql")
          .contentType(MediaType.APPLICATION_JSON)
          .bodyValue(graphQLRequest)
          .exchange()
          .expectStatus()
          .is2xxSuccessful()
          .expectHeader()
          .valueEquals(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE);
    } catch (IOException e) {
      // failed to close the server
    }
  }

  public void verifyMalformedCallbackInfo(WebTestClient testClient) {
    var graphQLRequest = createMockGraphQLRequest(null, null);
    testClient
        .post()
        .uri("/graphql")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(graphQLRequest)
        .exchange()
        .expectStatus()
        .is4xxClientError()
        .expectBody()
        .isEmpty();
  }

  public void verifyFailedCallbackInit(WebTestClient testClient) {
    // callback url doesn't exist
    var graphQLRequest =
        createMockGraphQLRequest(
            UUID.randomUUID().toString(), "http://does.not.exist/fake/callback");
    testClient
        .post()
        .uri("/graphql")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(graphQLRequest)
        .exchange()
        .expectStatus()
        .is4xxClientError()
        .expectBody()
        .isEmpty();
  }
}
