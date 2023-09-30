package com.apollographql.subscription.callback;

import com.apollographql.subscription.message.CallbackMessageCheck;
import com.apollographql.subscription.message.CallbackMessageComplete;
import com.apollographql.subscription.message.CallbackMessageNext;
import com.apollographql.subscription.message.SubscritionCallbackMessage;
import graphql.ExecutionResult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Map;

public class SubscriptionCallbackHandler {
  private static final Log logger = LogFactory.getLog(SubscriptionCallbackHandler.class);

  public static final String SUBSCRIPTION_PROTOCOL_HEADER = "subscription-protocol";
  public static final String SUBSCRIPTION_PROTOCOL_HEADER_VALUE = "callback/1.0";

  private final WebGraphQlHandler graphQlHandler;

  public SubscriptionCallbackHandler(WebGraphQlHandler graphQlHandler) {
    this.graphQlHandler = graphQlHandler;
  }

  @NotNull
  public Mono<Boolean> initCallback(WebGraphQlRequest graphQlRequest, SubscriptionCallback callback) {
    if (logger.isDebugEnabled()) {
      logger.debug("Starting subscription callback: " + callback);
    }

    // webclient that will be used for all communications
    var client = WebClient.builder()
      .baseUrl(callback.callback_url())
      .build();

    // check
    var checkMessage = new CallbackMessageCheck(callback.subscription_id(), callback.verifier());
    return client.post()
      .header("Content-Type", "application/json")
      .bodyValue(checkMessage)
      .exchangeToMono(checkResponse -> {
        var responseStatusCode = checkResponse.statusCode();
        var subscriptionProtocol = checkResponse.headers().header(SUBSCRIPTION_PROTOCOL_HEADER);

        if (HttpStatus.NO_CONTENT.equals(responseStatusCode)) {
          System.out.println("check successful");
//            && !subscriptionProtocol.isEmpty() && "callback".equals(subscriptionProtocol.get(0)))
          // start heartbeat
          Flux<SubscritionCallbackMessage> heartbeatFlux = heartbeatFlux(client, checkMessage);

          // start subscription
          Flux<SubscritionCallbackMessage> subscriptionResponse = this.graphQlHandler.handleRequest(graphQlRequest)
            .flatMapMany((subscriptionData) -> {
              Flux<Map<String, Object>> responseFlux;
              if (subscriptionData.getData() instanceof Publisher) {
                // Subscription
                responseFlux = Flux.from((Publisher<ExecutionResult>) subscriptionData.getData())
                  .map(ExecutionResult::toSpecification);
              }
              else {
                // should never be the case
                // Single response (query or mutation) that may contain errors
                responseFlux = Flux.just(subscriptionData.toMap());
              }
              return responseFlux
                .map((data) -> (SubscritionCallbackMessage)new CallbackMessageNext(callback.subscription_id(), callback.verifier(), data))
                .concatWith(Mono.just(new CallbackMessageComplete(callback.subscription_id(), callback.verifier())))
                .onErrorResume((e) -> {
                  // TODO map error to graphql error
                  return Mono.just(new CallbackMessageComplete(callback.subscription_id(), callback.verifier()));
                });
            })
            .publishOn(Schedulers.boundedElastic())
            .flatMap((message) -> { // change to map to short-circuit when post fails
                return client.post()
                  .header("Content-Type", "application/json")
                  .header(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE)
                  .bodyValue(message)
                  .exchangeToMono((routerResponse) -> {
                    if (HttpStatus.OK.equals(routerResponse.statusCode()) || HttpStatus.NO_CONTENT.equals(routerResponse.statusCode())) {
                      return Mono.just(message);
                    } else {
                      return Mono.error(new RuntimeException("failed to communicate with router"));
                    }
                  });
            })
            .publish()
            .refCount(2);

          Flux subscription = Flux.merge(
              subscriptionResponse,
              heartbeatFlux.takeUntilOther(subscriptionResponse.ignoreElements())
            )
            .subscribeOn(Schedulers.boundedElastic());

          return Mono.just(true)
            .publishOn(Schedulers.boundedElastic())
            .doOnSubscribe((subscribed) -> subscription.subscribe());
        } else {
          return Mono.just(false);
        }
      });
  }

  private Flux<SubscritionCallbackMessage> heartbeatFlux(WebClient client, CallbackMessageCheck check) {
    return scheduleHeartbeat(check)
      .flatMap((heartbeat) -> client.post()
        .header("Content-Type", "application/json")
        .bodyValue(heartbeat)
        .exchangeToFlux((heartBeatResponse) -> {
          var heartbeatStatusCode = heartBeatResponse.statusCode();
          if (HttpStatus.NO_CONTENT.equals(heartbeatStatusCode)) {
            return scheduleHeartbeat(heartbeat);
          } else {
            return Flux.error(new RuntimeException("inactive subscription"));
          }
        }));
  }

  private Flux<CallbackMessageCheck> scheduleHeartbeat(CallbackMessageCheck heartbeat) {
    return Flux.just(heartbeat)
      .delayElements(Duration.ofMillis(5000));
  }
}
