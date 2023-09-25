package com.apollographql.subscription.webmvc;

import com.apollographql.subscription.SubscriptionCallback;
import com.apollographql.subscription.SubscriptionCallbackResponse;
import com.apollographql.subscription.message.CallbackMessageCheck;
import com.apollographql.subscription.message.CallbackMessageComplete;
import com.apollographql.subscription.message.CallbackMessageHeartbeat;
import com.apollographql.subscription.message.CallbackMessageNext;
import com.apollographql.subscription.message.SubscritionCallbackMessage;
import graphql.ExecutionResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.webmvc.GraphQlHttpHandler;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.IdGenerator;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CallbackGraphQlHttpHandler extends GraphQlHttpHandler {

  private ConcurrentHashMap<String, Runnable> activeSubscriptions = new ConcurrentHashMap<>();
  private ExecutorService subscriptionThreadPool = Executors.newFixedThreadPool(10);
  private ScheduledExecutorService heartbeatExecutors = Executors.newScheduledThreadPool(0);


  private static final Log logger = LogFactory.getLog(GraphQlHttpHandler.class);

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_PARAMETERIZED_TYPE_REF =
    new ParameterizedTypeReference<Map<String, Object>>() {};

  // To be removed in favor of Framework's MediaType.APPLICATION_GRAPHQL_RESPONSE
  private static final MediaType APPLICATION_GRAPHQL_RESPONSE =
    new MediaType("application", "graphql-response+json");

  @SuppressWarnings("removal")
  private static final List<MediaType> SUPPORTED_MEDIA_TYPES =
    Arrays.asList(APPLICATION_GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON, MediaType.APPLICATION_GRAPHQL);

  private final IdGenerator idGenerator = new AlternativeJdkIdGenerator();

  private final WebGraphQlHandler graphQlHandler;


  public CallbackGraphQlHttpHandler(WebGraphQlHandler graphQlHandler) {
    super(graphQlHandler);
    this.graphQlHandler = graphQlHandler;
  }

  @Override
  public @NotNull ServerResponse handleRequest(@NotNull ServerRequest serverRequest) throws ServletException {
    WebGraphQlRequest graphQlRequest = new WebGraphQlRequest(
      serverRequest.uri(), serverRequest.headers().asHttpHeaders(), initCookies(serverRequest),
      serverRequest.attributes(), readBody(serverRequest), this.idGenerator.generateId().toString(),
      LocaleContextHolder.getLocale());

    if (logger.isDebugEnabled()) {
      logger.debug("Executing: " + graphQlRequest);
    }

    var callback = extractSubscriptionCallback(graphQlRequest.getExtensions());
    if (graphQlRequest.getDocument().startsWith("subscription") && callback != null) {
        // do callback
      var client = WebClient.builder()
        .baseUrl(callback.callback_url())
        .build();

      var routerResponse = client.post()
        .bodyValue(new CallbackMessageCheck(callback.subscription_id(), callback.verifier()))
        .exchangeToMono(response -> {
          var responseStatusCode = response.statusCode();
          var subscriptionProtocol = response.headers().header("subscription-protocol");
          // todo check empty body
          if (HttpStatus.NO_CONTENT.equals(responseStatusCode) && !subscriptionProtocol.isEmpty() && "callback".equals(subscriptionProtocol.get(0))) {
            return Mono.just(HttpStatus.OK);
          } else {
            return Mono.just(HttpStatus.BAD_REQUEST);
          }
        }).block();

      if (HttpStatus.OK.equals(routerResponse)) {
        var heartbeat = heartbeatExecutors.scheduleAtFixedRate(() -> {
          var hartbeatResponse = client.post()
            .bodyValue(new CallbackMessageHeartbeat(callback.subscription_id(), callback.verifier()))
            .retrieve()
            .bodyToMono(SubscriptionCallbackResponse.class)
            .block();
          // TODO check response
        }, 5000, 5000, TimeUnit.MILLISECONDS);
        Flux<SubscritionCallbackMessage> subscriptionResponse = this.graphQlHandler.handleRequest(graphQlRequest)
          .flatMapMany((response) -> {
            Flux<Map<String, Object>> responseFlux;
            if (response.getData() instanceof Publisher) {
              // Subscription
              responseFlux = Flux.from((Publisher<ExecutionResult>) response.getData())
                .map(ExecutionResult::toSpecification);
            }
            else {
              // Single response (query or mutation) that may contain errors
              responseFlux = Flux.just(response.toMap());
            }
            return responseFlux
              .map((data) -> (SubscritionCallbackMessage)new CallbackMessageNext(callback.subscription_id(), callback.verifier(), data));
          })
          .concatWith(Mono.just(new CallbackMessageComplete(callback.subscription_id(), callback.verifier())));
        subscriptionThreadPool.submit(new Runnable() {
          @Override
          public void run() {
            subscriptionResponse.doOnSubscribe((subscription) -> {
              Runnable prev = activeSubscriptions.putIfAbsent(callback.subscription_id(), this);
//                  if (prev != null) {
//                    // TODO subscription already exists;
//                  }
            })
              .doOnEach(message -> {
                client.post().bodyValue(message).retrieve();
              })
              .doOnComplete(new Runnable() {
                @Override
                public void run() {
                  activeSubscriptions.remove(callback.subscription_id());
                  heartbeat.cancel(true);
                }
              })
              .subscribe();
          }
        });
        return ServerResponse.ok().build();
      } else {
        return ServerResponse.badRequest().build();
      }
    } else {
      Mono<ServerResponse> responseMono = this.graphQlHandler.handleRequest(graphQlRequest)
        .map(response -> {
          if (logger.isDebugEnabled()) {
            logger.debug("Execution complete");
          }
          ServerResponse.BodyBuilder builder = ServerResponse.ok();
          builder.headers(headers -> headers.putAll(response.getResponseHeaders()));
          builder.contentType(selectResponseMediaType(serverRequest));
          return builder.body(response.toMap());
        });

      return ServerResponse.async(responseMono);
    }
  }


  private static MultiValueMap<String, HttpCookie> initCookies(ServerRequest serverRequest) {
    MultiValueMap<String, Cookie> source = serverRequest.cookies();
    MultiValueMap<String, HttpCookie> target = new LinkedMultiValueMap<>(source.size());
    source.values().forEach(cookieList -> cookieList.forEach(cookie -> {
      HttpCookie httpCookie = new HttpCookie(cookie.getName(), cookie.getValue());
      target.add(cookie.getName(), httpCookie);
    }));
    return target;
  }

  private static Map<String, Object> readBody(ServerRequest request) throws ServletException {
    try {
      return request.body(MAP_PARAMETERIZED_TYPE_REF);
    }
    catch (IOException ex) {
      throw new ServerWebInputException("I/O error while reading request body", null, ex);
    }
  }

  private static MediaType selectResponseMediaType(ServerRequest serverRequest) {
    for (MediaType accepted : serverRequest.headers().accept()) {
      if (SUPPORTED_MEDIA_TYPES.contains(accepted)) {
        return accepted;
      }
    }
    return MediaType.APPLICATION_JSON;
  }

  private SubscriptionCallback extractSubscriptionCallback(Map<String, Object> extensions) {
    var subscription_extension = extensions.get("subscription");
    if (subscription_extension instanceof Map subscription) {
      var callback_url = subscription.get("callback_url");
      var subscription_id = subscription.get("subscription_id");
      var verifier = subscription.get("verifier");

      if (callback_url != null && subscription_id != null && verifier != null) {
        return new SubscriptionCallback((String)callback_url, (String)subscription_id, (String)verifier);
      }
    }
    return null;
  }
}
