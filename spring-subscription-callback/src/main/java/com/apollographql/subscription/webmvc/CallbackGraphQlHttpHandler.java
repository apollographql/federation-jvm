package com.apollographql.subscription.webmvc;

import static com.apollographql.subscription.callback.SubscriptionCallback.parseSubscriptionCallbackExtension;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER_VALUE;

import com.apollographql.subscription.callback.SubscriptionCallbackHandler;
import graphql.ExecutionResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.webmvc.GraphQlHttpHandler;
import org.springframework.http.HttpCookie;
import org.springframework.http.MediaType;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.IdGenerator;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import reactor.core.publisher.Mono;

/**
 * Functional GraphQL handler for WebMVC applications that supports Apollo Subscription Callback
 * Protocol.
 *
 * @see <a
 *     href="https://www.apollographql.com/docs/router/executing-operations/subscription-callback-protocol">Subscription
 *     Callback Protocol</a>
 * @see org.springframework.graphql.server.webmvc.GraphQlHttpHandler
 */
public class CallbackGraphQlHttpHandler extends GraphQlHttpHandler {

  private static final Log logger = LogFactory.getLog(GraphQlHttpHandler.class);

  private static final ParameterizedTypeReference<Map<String, Object>> MAP_PARAMETERIZED_TYPE_REF =
      new ParameterizedTypeReference<>() {};

  private static final MediaType APPLICATION_GRAPHQL_RESPONSE =
      MediaType.APPLICATION_GRAPHQL_RESPONSE;

  @SuppressWarnings("removal")
  private static final List<MediaType> SUPPORTED_MEDIA_TYPES =
      Arrays.asList(
          APPLICATION_GRAPHQL_RESPONSE, MediaType.APPLICATION_JSON, MediaType.APPLICATION_GRAPHQL);

  private final IdGenerator idGenerator = new AlternativeJdkIdGenerator();

  private final WebGraphQlHandler graphQlHandler;
  private final SubscriptionCallbackHandler subscriptionCallbackHandler;

  public CallbackGraphQlHttpHandler(WebGraphQlHandler graphQlHandler) {
    this(graphQlHandler, new SubscriptionCallbackHandler(graphQlHandler));
  }

  public CallbackGraphQlHttpHandler(
      WebGraphQlHandler graphQlHandler, SubscriptionCallbackHandler subscriptionCallbackHandler) {
    super(graphQlHandler);
    this.graphQlHandler = graphQlHandler;
    this.subscriptionCallbackHandler = subscriptionCallbackHandler;
  }

  @Override
  public @NotNull ServerResponse handleRequest(@NotNull ServerRequest serverRequest)
      throws ServletException {
    WebGraphQlRequest graphQlRequest =
        new WebGraphQlRequest(
            serverRequest.uri(),
            serverRequest.headers().asHttpHeaders(),
            initCookies(serverRequest),
            serverRequest.attributes(),
            readBody(serverRequest),
            this.idGenerator.generateId().toString(),
            LocaleContextHolder.getLocale());

    if (logger.isDebugEnabled()) {
      logger.debug("Executing: " + graphQlRequest);
    }

    // in order to correctly handle parsing of ANY requests (i.e. it is valid to define a document
    // with query fragments first)
    // we would need to parse it which is a much heavier operation, we may opt to do it in the
    // future releases
    if (graphQlRequest.getDocument().startsWith("subscription")) {
      Mono<ServerResponse> responseMono =
          parseSubscriptionCallbackExtension(graphQlRequest.getExtensions())
              .flatMap(
                  (callback) -> {
                    if (logger.isDebugEnabled()) {
                      logger.debug("Starting subscription using callback: " + callback);
                    }
                    return this.subscriptionCallbackHandler
                      .handleSubscriptionUsingCallback(graphQlRequest, callback)
                      .map(
                        success -> {
                          if (success) {
                            var emptyResponse =
                              ExecutionResult.newExecutionResult().data(null).build();
                            var builder = ServerResponse.ok();
                            builder.header(
                              SUBSCRIPTION_PROTOCOL_HEADER,
                              SUBSCRIPTION_PROTOCOL_HEADER_VALUE);
                            builder.contentType(selectResponseMediaType(serverRequest));
                            return builder.body(emptyResponse.toSpecification());
                          } else {
                            return ServerResponse.badRequest().build();
                          }
                        });
                  })
              .onErrorResume(
                  (error) -> {
                    if (logger.isErrorEnabled()) {
                      logger.error("Unable to start subscription using callback protocol", error);
                    }
                    return Mono.just(ServerResponse.badRequest().build());
                  });
      return ServerResponse.async(responseMono);
    } else {
      Mono<ServerResponse> responseMono =
          this.graphQlHandler
              .handleRequest(graphQlRequest)
              .map(
                  response -> {
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
    source
        .values()
        .forEach(
            cookieList ->
                cookieList.forEach(
                    cookie -> {
                      HttpCookie httpCookie = new HttpCookie(cookie.getName(), cookie.getValue());
                      target.add(cookie.getName(), httpCookie);
                    }));
    return target;
  }

  private static Map<String, Object> readBody(ServerRequest request) throws ServletException {
    try {
      return request.body(MAP_PARAMETERIZED_TYPE_REF);
    } catch (IOException ex) {
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
}
