package com.apollographql.subscription.webmvc;

import com.apollographql.subscription.CallbackGraphQLHttpHandlerAbstrIT;
import com.apollographql.subscription.CallbackWebGraphQLInterceptor;
import com.apollographql.subscription.callback.SubscriptionCallbackHandler;
import com.apollographql.subscription.configuration.TestGraphQLConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
    classes = CallbackGraphQLWebmvcHttpHandlerIT.GraphQLSubscriptionConfiguration.class,
    properties = {
      "spring.main.web-application-type=servlet",
      "spring.graphql.websocket.path=/subscription",
    })
public class CallbackGraphQLWebmvcHttpHandlerIT extends CallbackGraphQLHttpHandlerAbstrIT {

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
      return new CallbackWebGraphQLInterceptor(callbackHandler);
    }
  }

  @Autowired private WebTestClient testClient;
  @LocalServerPort private int serverPort;

  @Test
  public void queries_works() {
    verifyQueriesWorks(testClient);
  }

  @Test
  public void webSocketSubscription_works() {
    var url = "http://localhost:" + serverPort + "/subscription";
    verifyWebSocketSubscriptionWorks(url);
  }

  @Test
  public void postSubscription_withoutCallback_returns404() {
    verifyPostSubscriptionsWithoutCallbackDontWork(testClient);
  }

  @Test
  public void callback_initSuccessful_returns200() {
    verifySuccessfulCallbackInit(testClient);
  }

  @Test
  public void callback_initFailed_returns404() {
    verifyFailedCallbackInit(testClient);
  }

  @Test
  public void callback_malformedRequest_returns404() {
    verifyMalformedCallbackInfo(testClient);
  }
}
