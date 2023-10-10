package com.apollographql.subscription.callback;

import static com.apollographql.subscription.CallbackTestUtils.createMockGraphQLRequest;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER_VALUE;

import com.apollographql.subscription.message.CallbackMessageCheck;
import com.apollographql.subscription.message.CallbackMessageComplete;
import com.apollographql.subscription.message.CallbackMessageNext;
import com.fasterxml.jackson.databind.ObjectMapper;
import graphql.ExecutionResult;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

public class SubscriptionCallbackHandlerTest {

  static class MockWebHandler implements WebGraphQlHandler {

    private Flux subscriptionFlux;

    public MockWebHandler(Flux subscriptionFlux) {
      this.subscriptionFlux = subscriptionFlux;
    }

    @Override
    public WebSocketGraphQlInterceptor getWebSocketInterceptor() {
      return null;
    }

    @Override
    public Mono<WebGraphQlResponse> handleRequest(WebGraphQlRequest request) {
      var executionResult = ExecutionResult.newExecutionResult().data(subscriptionFlux).build();
      var executionResponse =
          new DefaultExecutionGraphQlResponse(request.toExecutionInput(), executionResult);
      var response = new WebGraphQlResponse(executionResponse);
      return Mono.just(response);
    }
  }

  @Test
  public void init_successful() {
    var capturedRequests = new ArrayList<String>();
    try (var server = new MockWebServer()) {
      server.setDispatcher(
          new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
              capturedRequests.add(recordedRequest.getBody().readUtf8());
              return new MockResponse()
                  .setResponseCode(HttpStatus.NO_CONTENT.value())
                  .setHeader(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE);
            }
          });

      var data =
          Flux.just(1, 2)
              .map((i) -> ExecutionResult.newExecutionResult().data(Map.of("counter", i)).build());
      var handler = new SubscriptionCallbackHandler(new MockWebHandler(data));

      var subscriptionId = UUID.randomUUID().toString();
      var callbackUrl = server.url("/callback/" + subscriptionId).toString();
      var verifier = "junit";
      var callback = new SubscriptionCallback(callbackUrl, subscriptionId, verifier);

      var graphQLRequest = stubWebGraphQLRequest(subscriptionId, callbackUrl);
      var subscription = handler.handleSubscriptionUsingCallback(graphQLRequest, callback);
      StepVerifier.create(subscription).expectNext(true).verifyComplete();

      // wait for subscription to end
      // TODO change to virtual time
      Thread.sleep(500);

      var objectMapper = new ObjectMapper();
      Assertions.assertEquals(4, capturedRequests.size());
      Assertions.assertEquals(
          new CallbackMessageCheck(subscriptionId, callbackUrl),
          objectMapper.readValue(capturedRequests.get(0), CallbackMessageCheck.class));
      Assertions.assertEquals(
          nextMessage(subscriptionId, callbackUrl, 1),
          objectMapper.readValue(capturedRequests.get(1), CallbackMessageNext.class));
      Assertions.assertEquals(
          nextMessage(subscriptionId, callbackUrl, 2),
          objectMapper.readValue(capturedRequests.get(2), CallbackMessageNext.class));
      Assertions.assertEquals(
          new CallbackMessageComplete(subscriptionId, callbackUrl),
          objectMapper.readValue(capturedRequests.get(3), CallbackMessageComplete.class));
    } catch (IOException | InterruptedException e) {
      // failed to close the server
    }
  }

  @Test
  public void init_failed() {
    var capturedRequests = new ArrayList<String>();
    try (var server = new MockWebServer()) {
      server.setDispatcher(
          new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
              capturedRequests.add(recordedRequest.getBody().readUtf8());
              return new MockResponse().setResponseCode(HttpStatus.NOT_FOUND.value());
            }
          });

      var data =
          Flux.just(1, 2)
              .map((i) -> ExecutionResult.newExecutionResult().data(Map.of("counter", i)).build());
      var handler = new SubscriptionCallbackHandler(new MockWebHandler(data));

      var subscriptionId = UUID.randomUUID().toString();
      var callbackUrl = server.url("/callback/" + subscriptionId).toString();
      var verifier = "junit";
      var callback = new SubscriptionCallback(callbackUrl, subscriptionId, verifier);

      var graphQLRequest = stubWebGraphQLRequest(subscriptionId, callbackUrl);
      var subscription = handler.handleSubscriptionUsingCallback(graphQLRequest, callback);
      StepVerifier.create(subscription).expectNext(false).verifyComplete();

      // wait for subscription to end
      // TODO change to virtual time
      Thread.sleep(500);

      var objectMapper = new ObjectMapper();
      Assertions.assertEquals(1, capturedRequests.size());
      Assertions.assertEquals(
          new CallbackMessageCheck(subscriptionId, callbackUrl),
          objectMapper.readValue(capturedRequests.get(0), CallbackMessageCheck.class));
    } catch (IOException | InterruptedException e) {
      // failed to close the server
    }
  }

  @Test
  public void heartbeat() {
    var capturedRequests = new ArrayList<String>();
    try (var server = new MockWebServer()) {
      server.setDispatcher(
          new Dispatcher() {
            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
              capturedRequests.add(recordedRequest.getBody().readUtf8());
              return new MockResponse()
                  .setResponseCode(HttpStatus.OK.value())
                  .setHeader(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE);
            }
          });

      var subscriptionId = UUID.randomUUID().toString();
      var callbackUrl = server.url("/callback/" + subscriptionId).toString();
      var verifier = "junit";
      var callback = new SubscriptionCallback(callbackUrl, subscriptionId, verifier);
      var client = WebClient.builder().baseUrl(callback.callback_url()).build();

      var graphQLRequest = stubWebGraphQLRequest(subscriptionId, callbackUrl);
      var data =
          Flux.just(1, 2)
              .delayElements(Duration.ofMillis(3000))
              .map((i) -> ExecutionResult.newExecutionResult().data(Map.of("counter", i)).build());
      var handler = new SubscriptionCallbackHandler(new MockWebHandler(data));

      // note: heartbeat goes into infinite recursion and does not emit value
      // TODO update to use virtual timer
      StepVerifier.create(handler.startSubscription(client, graphQLRequest, callback))
          .expectNext(nextMessage(subscriptionId, verifier, 1))
          .expectNext(nextMessage(subscriptionId, verifier, 2))
          .expectNext(new CallbackMessageComplete(subscriptionId, verifier))
          .expectComplete()
          .verify();

      var objectMapper = new ObjectMapper();
      Assertions.assertEquals(4, capturedRequests.size());
      Assertions.assertEquals(
          nextMessage(subscriptionId, callbackUrl, 1),
          objectMapper.readValue(capturedRequests.get(0), CallbackMessageNext.class));
      Assertions.assertEquals(
          new CallbackMessageCheck(subscriptionId, callbackUrl),
          objectMapper.readValue(capturedRequests.get(1), CallbackMessageCheck.class));
      Assertions.assertEquals(
          nextMessage(subscriptionId, callbackUrl, 2),
          objectMapper.readValue(capturedRequests.get(2), CallbackMessageNext.class));
      Assertions.assertEquals(
          new CallbackMessageComplete(subscriptionId, callbackUrl),
          objectMapper.readValue(capturedRequests.get(3), CallbackMessageComplete.class));
    } catch (IOException e) {
      // failed to close the server
    }
  }

  @Test
  public void heartbeat_failed() {
    var capturedRequests = new ArrayList<String>();
    try (var server = new MockWebServer()) {
      server.setDispatcher(
          new Dispatcher() {
            final AtomicBoolean heartbeat = new AtomicBoolean(false);

            @NotNull
            @Override
            public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
              capturedRequests.add(recordedRequest.getBody().readUtf8());
              if (heartbeat.getAndSet(true)) {
                return new MockResponse()
                    .setResponseCode(HttpStatus.NOT_FOUND.value())
                    .setHeader(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE);
              } else {
                return new MockResponse()
                    .setResponseCode(HttpStatus.OK.value())
                    .setHeader(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE);
              }
            }
          });

      var subscriptionId = UUID.randomUUID().toString();
      var callbackUrl = server.url("/callback/" + subscriptionId).toString();
      var verifier = "junit";
      var callback = new SubscriptionCallback(callbackUrl, subscriptionId, verifier);
      var client = WebClient.builder().baseUrl(callback.callback_url()).build();

      var graphQLRequest = stubWebGraphQLRequest(subscriptionId, callbackUrl);
      var data =
          Flux.just(1, 2)
              .delayElements(Duration.ofMillis(3000))
              .map((i) -> ExecutionResult.newExecutionResult().data(Map.of("counter", i)).build());
      var handler = new SubscriptionCallbackHandler(new MockWebHandler(data));

      // note: heartbeat goes into infinite recursion and does not emit value
      // TODO update to use virtual timer
      StepVerifier.create(handler.startSubscription(client, graphQLRequest, callback))
          .expectNext(nextMessage(subscriptionId, verifier, 1))
          .expectError()
          .verify();

      var objectMapper = new ObjectMapper();
      Assertions.assertEquals(2, capturedRequests.size());
      Assertions.assertEquals(
          nextMessage(subscriptionId, callbackUrl, 1),
          objectMapper.readValue(capturedRequests.get(0), CallbackMessageNext.class));
      Assertions.assertEquals(
          new CallbackMessageCheck(subscriptionId, callbackUrl),
          objectMapper.readValue(capturedRequests.get(1), CallbackMessageCheck.class));
    } catch (IOException e) {
      // failed to close the server
    }
  }

  @Test
  public void subscription_success() {
    try (var server = new MockWebServer()) {
      mockServerResponses(server, HttpStatus.OK, HttpStatus.OK, HttpStatus.ACCEPTED);

      var data =
          Flux.just(1, 2)
              .delayElements(Duration.ofMillis(50))
              .map((i) -> ExecutionResult.newExecutionResult().data(Map.of("counter", i)).build());
      var handler = new SubscriptionCallbackHandler(new MockWebHandler(data));

      var subscriptionId = UUID.randomUUID().toString();
      var callbackUrl = server.url("/callback/" + subscriptionId).toString();
      var verifier = "junit";
      var callback = new SubscriptionCallback(callbackUrl, subscriptionId, verifier);
      var client = WebClient.builder().baseUrl(callback.callback_url()).build();

      var graphQLRequest = stubWebGraphQLRequest(subscriptionId, callbackUrl);
      var subscription = handler.startSubscription(client, graphQLRequest, callback);
      StepVerifier.create(subscription)
          .expectNext(nextMessage(subscriptionId, verifier, 1))
          .expectNext(nextMessage(subscriptionId, verifier, 2))
          .expectNext(new CallbackMessageComplete(subscriptionId, verifier))
          .verifyComplete();
    } catch (IOException e) {
      // failed to close the server
    }
  }

  @Test
  public void subscription_exception() {
    try (var server = new MockWebServer()) {
      mockServerResponses(server, HttpStatus.OK, HttpStatus.OK, HttpStatus.ACCEPTED);

      var data =
          Flux.just(1, 2)
              .delayElements(Duration.ofMillis(50))
              .map((i) -> ExecutionResult.newExecutionResult().data(Map.of("counter", i)).build())
              .concatWith(Mono.error(new RuntimeException("JUNIT_FAILURE")));
      var handler = new SubscriptionCallbackHandler(new MockWebHandler(data));

      var subscriptionId = UUID.randomUUID().toString();
      var callbackUrl = server.url("/callback/" + subscriptionId).toString();
      var verifier = "junit";
      var callback = new SubscriptionCallback(callbackUrl, subscriptionId, verifier);
      var client = WebClient.builder().baseUrl(callback.callback_url()).build();

      var graphQLRequest = stubWebGraphQLRequest(subscriptionId, callbackUrl);
      var subscription = handler.startSubscription(client, graphQLRequest, callback);
      StepVerifier.create(subscription)
          .expectNext(nextMessage(subscriptionId, verifier, 1))
          .expectNext(nextMessage(subscriptionId, verifier, 2))
          .expectNextMatches(
              (msg) -> {
                if (msg instanceof CallbackMessageComplete complete) {
                  // GraphQL error does not implement hashcode + equals
                  return subscriptionId.equals(complete.getId())
                      && verifier.equals(complete.getVerifier())
                      && complete.errors().size() == 1
                      && "JUNIT_FAILURE".equals(complete.errors().get(0).getMessage());
                } else {
                  return false;
                }
              })
          .verifyComplete();
    } catch (IOException e) {
      // failed to close the server
    }
  }

  private void mockServerResponses(MockWebServer server, HttpStatus... codes) throws IOException {
    for (HttpStatus code : codes) {
      server.enqueue(
          new MockResponse()
              .setResponseCode(code.value())
              .setHeader(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE));
    }
    server.start();
  }

  private WebGraphQlRequest stubWebGraphQLRequest(String subscriptionId, String callbackUrl) {
    return new WebGraphQlRequest(
        URI.create(callbackUrl),
        HttpHeaders.EMPTY,
        null,
        Collections.emptyMap(),
        createMockGraphQLRequest(subscriptionId, callbackUrl),
        UUID.randomUUID().toString(),
        Locale.US);
  }

  private CallbackMessageNext nextMessage(String subscriptionId, String verifier, int value) {
    return new CallbackMessageNext(
        subscriptionId, verifier, Map.of("data", Map.of("counter", value)));
  }
}
