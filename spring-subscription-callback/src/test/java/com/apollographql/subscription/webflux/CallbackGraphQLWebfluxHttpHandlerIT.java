package com.apollographql.subscription.webflux;

import com.apollographql.subscription.CallbackGraphQLHttpHandlerAbstrIT;
import com.apollographql.subscription.CallbackWebGraphQLInterceptor;
import com.apollographql.subscription.callback.SubscriptionCallbackHandler;
import com.apollographql.subscription.configuration.TestGraphQLConfiguration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.graphql.ExecutionGraphQlService;
import org.springframework.test.web.reactive.server.WebTestClient;

@EnableAutoConfiguration
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = CallbackGraphQLWebfluxHttpHandlerIT.GraphQLSubscriptionConfiguration.class,
    properties = {
      "spring.main.web-application-type=reactive",
      "spring.graphql.websocket.path=/subscription",
    })
public class CallbackGraphQLWebfluxHttpHandlerIT extends CallbackGraphQLHttpHandlerAbstrIT {

  @Import(TestGraphQLConfiguration.class)
  @Configuration
  @ComponentScan(basePackages = "com.apollographql.subscription.configuration")
  static class GraphQLSubscriptionConfiguration {

    @Bean
    public SubscriptionCallbackHandler callbackHandler(ExecutionGraphQlService graphQlService) {
      return new SubscriptionCallbackHandler(graphQlService);
    }

    @Bean
    public CallbackWebGraphQLInterceptor callbackGraphQlInterceptor(
        SubscriptionCallbackHandler callbackHandler) {
      var headers = Set.of(TEST_HEADER_NAME);
      return new CallbackWebGraphQLInterceptor(callbackHandler, headers);
    }
  }

  @LocalServerPort private int serverPort;

  private WebTestClient getTestClient() {
    return WebTestClient.bindToServer().baseUrl("http://localhost:" + serverPort).build();
  }

  @Test
  public void queries_works() {
    verifyQueriesWorks(getTestClient());
  }

  @Test
  public void webSocketSubscription_works() {
    var url = "ws://localhost:" + serverPort + "/subscription";
    verifyWebSocketSubscriptionWorks(url);
  }

  @Test
  public void callbackSubscription_works() {
    verifySuccessfulCallbackSubscription(getTestClient());
  }

  @Test
  public void callbackSubscription_withHeaders_works() {
    verifySuccessfulCallbackSubscriptionWithHeaders(getTestClient());
  }

  @Test
  public void postSubscription_withoutCallback_returns404() {
    verifyPostSubscriptionsWithoutCallbackDontWork(getTestClient());
  }

  @Test
  public void callback_initFailed_returns404() {
    verifyFailedCallbackInit(getTestClient());
  }

  @Test
  public void callback_malformedRequest_returns404() {
    verifyMalformedCallbackInfo(getTestClient());
  }
}
