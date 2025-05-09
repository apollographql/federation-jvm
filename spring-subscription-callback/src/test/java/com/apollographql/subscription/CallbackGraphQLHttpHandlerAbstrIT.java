package com.apollographql.subscription;

import static com.apollographql.subscription.CallbackTestUtils.createMockGraphQLRequest;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER_VALUE;

import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Assert;
import org.springframework.graphql.test.tester.WebSocketGraphQlTester;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import reactor.test.StepVerifier;

public abstract class CallbackGraphQLHttpHandlerAbstrIT {
  public static String TEST_HEADER_NAME = "X-Test-Header";
  public static String TEST_HEADER_VALUE = "junitTEST123";

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

  public void verifySuccessfulCallbackSubscription(WebTestClient testClient) {
    verifyCallbackSubscription(testClient, false);
  }

  public void verifySuccessfulCallbackSubscriptionWithHeaders(WebTestClient testClient) {
    verifyCallbackSubscription(testClient, true);
  }

  private void verifyCallbackSubscription(WebTestClient testClient, boolean testHeaders) {
    try (MockWebServer server = new MockWebServer()) {
      server.enqueue(
          new MockResponse()
              .setResponseCode(HttpStatus.NO_CONTENT.value())
              .setHeader(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE));
      server.enqueue(new MockResponse().setResponseCode(HttpStatus.NO_CONTENT.value()));
      server.enqueue(new MockResponse().setResponseCode(HttpStatus.NO_CONTENT.value()));
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
          .headers(
              headers -> {
                if (testHeaders) {
                  headers.put(TEST_HEADER_NAME, List.of(TEST_HEADER_VALUE));
                }
              })
          .bodyValue(graphQLRequest)
          .exchange()
          .expectStatus()
          .is2xxSuccessful()
          .expectHeader()
          .valueEquals(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE);

      var checkMessageRaw = server.takeRequest(1, TimeUnit.SECONDS);
      Assert.assertNotNull(checkMessageRaw);
      if (testHeaders) {
        var headerValue = checkMessageRaw.getHeader(TEST_HEADER_NAME);
        Assert.assertEquals(TEST_HEADER_VALUE, headerValue);
      }
      var checkMessage = JsonPath.parse(checkMessageRaw.getBody().inputStream());
      Assert.assertEquals(subscriptionId, checkMessage.read("$.id"));

      for (int i : List.of(1, 2, 3)) {
        var nextMessageRaw = server.takeRequest(1, TimeUnit.SECONDS);
        Assert.assertNotNull(nextMessageRaw);
        if (testHeaders) {
          var headerValue = checkMessageRaw.getHeader(TEST_HEADER_NAME);
          Assert.assertEquals(TEST_HEADER_VALUE, headerValue);
        }
        var nextMessage = JsonPath.parse(nextMessageRaw.getBody().inputStream());
        Assert.assertEquals(subscriptionId, nextMessage.read("$.id"));
        Assert.assertEquals(Integer.valueOf(i), nextMessage.read("$.payload.data.counter"));
      }

      var completeMessageRaw = server.takeRequest(1, TimeUnit.SECONDS);
      Assert.assertNotNull(completeMessageRaw);
      var completeMessage = JsonPath.parse(completeMessageRaw.getBody().inputStream());
      Assert.assertEquals(subscriptionId, completeMessage.read("$.id"));
      Assert.assertEquals("complete", completeMessage.read("$.action"));
    } catch (IOException e) {
      // failed to close the server
    } catch (InterruptedException e) {
      Assert.fail("Failed to read callback data. Exception: " + e.getMessage());
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
        .is4xxClientError();
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
        .is4xxClientError();
  }
}
