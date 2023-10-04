package com.apollographql.subscription.callback;

import com.apollographql.subscription.exception.InactiveSubscriptionException;
import com.apollographql.subscription.message.CallbackMessageCheck;
import com.apollographql.subscription.message.CallbackMessageComplete;
import com.apollographql.subscription.message.CallbackMessageNext;
import com.apollographql.subscription.message.SubscritionCallbackMessage;
import graphql.ExecutionResult;
import graphql.GraphqlErrorBuilder;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/** GraphQL subscription handler implementing Apollo HTTP callback protocol. */
public class SubscriptionCallbackHandler {
  private static final Log logger = LogFactory.getLog(SubscriptionCallbackHandler.class);

  public static final String SUBSCRIPTION_PROTOCOL_HEADER = "subscription-protocol";
  public static final String SUBSCRIPTION_PROTOCOL_HEADER_VALUE = "callback/1.0";

  private final WebGraphQlHandler graphQlHandler;
  private final Scheduler scheduler;

  public SubscriptionCallbackHandler(WebGraphQlHandler graphQlHandler) {
    this(graphQlHandler, Schedulers.boundedElastic());
  }

  public SubscriptionCallbackHandler(WebGraphQlHandler graphQlHandler, Scheduler scheduler) {
    this.graphQlHandler = graphQlHandler;
    this.scheduler = scheduler;
  }

  @NotNull
  public Mono<Boolean> handleSubscriptionUsingCallback(
      @NotNull WebGraphQlRequest graphQlRequest, @NotNull SubscriptionCallback callback) {
    if (logger.isDebugEnabled()) {
      logger.debug("Starting subscription callback: " + callback);
    }

    // webclient that will be used for all communications
    var client = WebClient.builder().baseUrl(callback.callback_url()).build();

    // check
    var checkMessage = new CallbackMessageCheck(callback.subscription_id(), callback.verifier());
    return client
        .post()
        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
        .header(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE)
        .bodyValue(checkMessage)
        .exchangeToMono(
            checkResponse -> {
              var responseStatusCode = checkResponse.statusCode();
              var subscriptionProtocol =
                  checkResponse.headers().header(SUBSCRIPTION_PROTOCOL_HEADER);

              if (responseStatusCode.is2xxSuccessful()) {
                //            && !subscriptionProtocol.isEmpty() &&
                // "callback".equals(subscriptionProtocol.get(0)))
                Flux<SubscritionCallbackMessage> subscription =
                    startSubscription(client, graphQlRequest, callback);
                return Mono.just(true)
                    .publishOn(scheduler)
                    .doOnSubscribe((subscribed) -> subscription.subscribe());
              } else {
                return Mono.just(false);
              }
            })
        .onErrorReturn(false);
  }

  @NotNull
  protected Flux<SubscritionCallbackMessage> startSubscription(
      @NotNull WebClient callbackClient,
      @NotNull WebGraphQlRequest graphQlRequest,
      @NotNull SubscriptionCallback callback) {
    // start heartbeat
    var checkMessage = new CallbackMessageCheck(callback.subscription_id(), callback.verifier());
    Flux<SubscritionCallbackMessage> heartbeatFlux =
        heartbeatFlux(callbackClient, checkMessage, callback);

    // start subscription
    Flux<SubscritionCallbackMessage> subscriptionFlux =
        this.graphQlHandler
            .handleRequest(graphQlRequest)
            .flatMapMany(
                (subscriptionData) -> {
                  Flux<Map<String, Object>> responseFlux;
                  if (subscriptionData.getData() instanceof Publisher) {
                    // Subscription
                    responseFlux =
                        Flux.from((Publisher<ExecutionResult>) subscriptionData.getData())
                            .map(ExecutionResult::toSpecification);
                  } else {
                    // should never be the case
                    // Single response (query or mutation) that may contain errors
                    responseFlux = Flux.just(subscriptionData.toMap());
                  }
                  return responseFlux
                      .map(
                          (data) ->
                              (SubscritionCallbackMessage)
                                  new CallbackMessageNext(
                                      callback.subscription_id(), callback.verifier(), data))
                      .concatWith(
                          Mono.just(
                              new CallbackMessageComplete(
                                  callback.subscription_id(), callback.verifier())))
                      .onErrorResume(
                          (e) -> {
                            var error =
                                GraphqlErrorBuilder.newError().message(e.getMessage()).build();
                            return Mono.just(
                                new CallbackMessageComplete(
                                    callback.subscription_id(),
                                    callback.verifier(),
                                    List.of(error)));
                          });
                })
            .publishOn(scheduler)
            .concatMap(
                (message) ->
                    callbackClient
                        .post()
                        .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                        .header(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE)
                        .bodyValue(message)
                        .exchangeToMono(
                            (routerResponse) -> {
                              if (routerResponse.statusCode().is2xxSuccessful()) {
                                return Mono.just(message);
                              } else {
                                return Mono.error(new InactiveSubscriptionException(callback));
                              }
                            }))
            .publish()
            .refCount(2);

    return Flux.merge(
        subscriptionFlux, heartbeatFlux.takeUntilOther(subscriptionFlux.ignoreElements()));
  }

  private Flux<SubscritionCallbackMessage> heartbeatFlux(
      WebClient client, CallbackMessageCheck check, SubscriptionCallback callback) {
    return Flux.just(check)
        .delayElements(Duration.ofMillis(5000))
        .publishOn(scheduler)
        .concatMap(
            (heartbeat) ->
                client
                    .post()
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .header(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE)
                    .bodyValue(heartbeat)
                    .exchangeToFlux(
                        (heartBeatResponse) -> {
                          if (heartBeatResponse.statusCode().is2xxSuccessful()) {
                            return heartbeatFlux(client, heartbeat, callback);
                          } else {
                            return Flux.error(new InactiveSubscriptionException(callback));
                          }
                        }));
  }
}
