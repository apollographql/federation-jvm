package com.apollographql.subscription.webflux;

import com.apollographql.subscription.callback.SubscriptionCallbackHandler;
import graphql.ExecutionResult;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.webmvc.GraphQlHttpHandler;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.apollographql.subscription.callback.SubscriptionCallback.parseSubscriptionCallbackExtension;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER_VALUE;

/**
 * Copy of org.springframework.graphql.server.webflux.GraphQlHttpHandler
 */
public class CallbackGraphQlHttpHandler extends GraphQlHttpHandler {

  private static final Log logger = LogFactory.getLog(GraphQlHttpHandler.class);

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_PARAMETERIZED_TYPE_REF = new ParameterizedTypeReference<>() {};

  private static final MediaType APPLICATION_GRAPHQL_RESPONSE = MediaType.APPLICATION_GRAPHQL_RESPONSE;

  @SuppressWarnings("removal")
  private static final List<MediaType> SUPPORTED_MEDIA_TYPES =
    Arrays.asList(APPLICATION_GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON, MediaType.APPLICATION_GRAPHQL);

  private final WebGraphQlHandler graphQlHandler;
  private final SubscriptionCallbackHandler subscriptionCallbackHandler;

  public CallbackGraphQlHttpHandler(WebGraphQlHandler graphQlHandler, SubscriptionCallbackHandler subscriptionCallbackHandler) {
    super(graphQlHandler);
    this.graphQlHandler = graphQlHandler;
    this.subscriptionCallbackHandler = subscriptionCallbackHandler;
  }

  @NotNull
  public Mono<ServerResponse> handleRequest(@NotNull ServerRequest serverRequest) {
    return serverRequest.bodyToMono(MAP_PARAMETERIZED_TYPE_REF)
      .map(body -> new WebGraphQlRequest(
        serverRequest.uri(), serverRequest.headers().asHttpHeaders(),
        serverRequest.cookies(), serverRequest.attributes(), body,
        serverRequest.exchange().getRequest().getId(),
        serverRequest.exchange().getLocaleContext().getLocale()))
      .flatMap(graphQlRequest -> {
        if (logger.isDebugEnabled()) {
          logger.debug("Executing: " + graphQlRequest);
        }

        var callback = parseSubscriptionCallbackExtension(graphQlRequest.getExtensions());
        if (callback != null && graphQlRequest.getDocument().startsWith("subscription")) {
            return this.subscriptionCallbackHandler.initCallback(graphQlRequest, callback)
            .flatMap(success -> {
              if (success) {
                var emptyResponse = ExecutionResult.newExecutionResult()
                  .data(null)
                  .build();
                var builder = ServerResponse.ok();
                builder.header(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE);
                builder.contentType(selectResponseMediaType(serverRequest));
                return builder.bodyValue(emptyResponse.toSpecification());
              } else {
                return ServerResponse.badRequest().build();
              }
            });
        } else {
          return this.graphQlHandler.handleRequest(graphQlRequest)
            .flatMap(response -> {
              if (logger.isDebugEnabled()) {
                logger.debug("Execution complete");
              }
              ServerResponse.BodyBuilder builder = ServerResponse.ok();
              builder.headers(headers -> headers.putAll(response.getResponseHeaders()));
              builder.contentType(selectResponseMediaType(serverRequest));
              return builder.bodyValue(response.toMap());
            });
        }
      });
  }

  private static MediaType selectResponseMediaType(ServerRequest serverRequest) {
    for (MediaType accepted : serverRequest.headers().accept()) {
      if (SUPPORTED_MEDIA_TYPES.contains(accepted)) {
        return accepted;
      }
    }
    return MediaType.APPLICATION_JSON;
  }
}
