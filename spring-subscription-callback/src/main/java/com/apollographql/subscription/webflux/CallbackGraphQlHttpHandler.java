package com.apollographql.subscription.webflux;

import static com.apollographql.subscription.callback.SubscriptionCallback.parseSubscriptionCallbackExtension;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER_VALUE;

import com.apollographql.subscription.callback.SubscriptionCallbackHandler;
import graphql.ExecutionResult;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.reactivestreams.Publisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.Decoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.graphql.server.webflux.GraphQlHttpHandler;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.CodecConfigurer;
import org.springframework.http.codec.DecoderHttpMessageReader;
import org.springframework.lang.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import reactor.core.publisher.Mono;

/**
 * Functional GraphQL handler for reactive WebFlux applications that supports Apollo Subscription
 * Callback Protocol.
 *
 * @see <a
 *     href="https://www.apollographql.com/docs/router/executing-operations/subscription-callback-protocol">Subscription
 *     Callback Protocol</a>
 * @see org.springframework.graphql.server.webflux.GraphQlHttpHandler
 */
public class CallbackGraphQlHttpHandler extends GraphQlHttpHandler {

  private static final Log logger = LogFactory.getLog(GraphQlHttpHandler.class);

  private final WebGraphQlHandler graphQlHandler;
  private final SubscriptionCallbackHandler subscriptionCallbackHandler;
  @Nullable private final Decoder<?> decoder;

  public CallbackGraphQlHttpHandler(WebGraphQlHandler graphQlHandler) {
    this(graphQlHandler, new SubscriptionCallbackHandler(graphQlHandler));
  }

  public CallbackGraphQlHttpHandler(
      WebGraphQlHandler graphQlHandler, SubscriptionCallbackHandler subscriptionCallbackHandler) {
    this(graphQlHandler, subscriptionCallbackHandler, null);
  }

  public CallbackGraphQlHttpHandler(
      WebGraphQlHandler graphQlHandler,
      SubscriptionCallbackHandler subscriptionCallbackHandler,
      @Nullable CodecConfigurer codecConfigurer) {
    super(graphQlHandler, codecConfigurer);
    this.graphQlHandler = graphQlHandler;
    this.subscriptionCallbackHandler = subscriptionCallbackHandler;
    this.decoder = (codecConfigurer != null) ? findJsonDecoder(codecConfigurer) : null;
  }

  @NotNull
  public Mono<ServerResponse> handleRequest(@NotNull ServerRequest serverRequest) {
    return readRequest(serverRequest)
        .map(
            body ->
                new WebGraphQlRequest(
                    serverRequest.uri(),
                    serverRequest.headers().asHttpHeaders(),
                    serverRequest.cookies(),
                    serverRequest.remoteAddress().orElse(null),
                    serverRequest.attributes(),
                    body,
                    serverRequest.exchange().getRequest().getId(),
                    serverRequest.exchange().getLocaleContext().getLocale()))
        .flatMap(
            graphQlRequest -> {
              if (logger.isDebugEnabled()) {
                logger.debug("Executing: " + graphQlRequest);
              }

              // in order to correctly handle parsing of ANY requests (i.e. it is valid to define a
              // document with query fragments first) we would need to parse it which is a much
              // heavier
              // operation, we may opt to do it in the future releases
              if (graphQlRequest.getDocument().startsWith("subscription")) {
                return parseSubscriptionCallbackExtension(graphQlRequest.getExtensions())
                    .flatMap(
                        callback -> {
                          if (logger.isDebugEnabled()) {
                            logger.debug("Starting subscription using callback: " + callback);
                          }
                          return this.subscriptionCallbackHandler
                              .handleSubscriptionUsingCallback(graphQlRequest, callback)
                              .flatMap(
                                  success -> {
                                    if (success) {
                                      return prepareResponse(
                                          serverRequest, emptyResponse(graphQlRequest));
                                    } else {
                                      return ServerResponse.badRequest().build();
                                    }
                                  });
                        })
                    .onErrorResume(
                        (error) -> {
                          if (logger.isErrorEnabled()) {
                            logger.error(
                                "Unable to start subscription using callback protocol", error);
                          }
                          return ServerResponse.badRequest().build();
                        });
              } else {
                return this.graphQlHandler
                    .handleRequest(graphQlRequest)
                    .flatMap(
                        (response) -> {
                          if (logger.isDebugEnabled()) {
                            List<ResponseError> errors = response.getErrors();
                            logger.debug(
                                "Execution result "
                                    + (!CollectionUtils.isEmpty(errors)
                                        ? "has errors: " + errors
                                        : "is ready")
                                    + ".");
                          }

                          return prepareResponse(serverRequest, response);
                        });
              }
            });
  }

  private WebGraphQlResponse emptyResponse(WebGraphQlRequest request) {
    var emptyResponse = ExecutionResult.newExecutionResult().data(null).build();
    var emptyExecutionResponse =
        new DefaultExecutionGraphQlResponse(request.toExecutionInput(), emptyResponse);
    var emptyGraphQLResponse = new WebGraphQlResponse(emptyExecutionResponse);
    emptyGraphQLResponse
        .getResponseHeaders()
        .add(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE);
    return emptyGraphQLResponse;
  }

  // ========= CODE BELOW COPIED FROM SPRING =============
  // Those are all private static methods so sadly we cannot access them directly :(
  private static final ResolvableType REQUEST_TYPE =
      ResolvableType.forClass(SerializableGraphQlRequest.class);

  // copy from protected HttpCodecDelegate class
  private static Decoder<?> findJsonDecoder(CodecConfigurer configurer) {
    return configurer.getReaders().stream()
        .filter((reader) -> reader.canRead(REQUEST_TYPE, MediaType.APPLICATION_JSON))
        .map((reader) -> ((DecoderHttpMessageReader<?>) reader).getDecoder())
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("No JSON Decoder"));
  }

  @SuppressWarnings("unchecked")
  Mono<SerializableGraphQlRequest> decode(
      Publisher<DataBuffer> inputStream, MediaType contentType) {
    return (Mono<SerializableGraphQlRequest>)
        this.decoder.decodeToMono(inputStream, REQUEST_TYPE, contentType, null);
  }

  // copy from AbstractGraphQlHttpHandler
  private Mono<SerializableGraphQlRequest> readRequest(ServerRequest serverRequest) {
    if (this.decoder != null) {
      MediaType contentType =
          serverRequest.headers().contentType().orElse(MediaType.APPLICATION_JSON);
      return decode(serverRequest.bodyToFlux(DataBuffer.class), contentType);
    } else {
      return serverRequest
          .bodyToMono(SerializableGraphQlRequest.class)
          .onErrorResume(
              UnsupportedMediaTypeStatusException.class,
              (ex) -> applyApplicationGraphQlFallback(ex, serverRequest));
    }
  }

  // copy from AbstractGraphQlHttpHandler
  private static Mono<SerializableGraphQlRequest> applyApplicationGraphQlFallback(
      UnsupportedMediaTypeStatusException ex, ServerRequest request) {

    // Spec requires application/json but some clients still use application/graphql
    return "application/graphql".equals(request.headers().firstHeader(HttpHeaders.CONTENT_TYPE))
        ? ServerRequest.from(request)
            .headers((headers) -> headers.setContentType(MediaType.APPLICATION_JSON))
            .body(request.bodyToFlux(DataBuffer.class))
            .build()
            .bodyToMono(SerializableGraphQlRequest.class)
            .log()
        : Mono.error(ex);
  }
}
