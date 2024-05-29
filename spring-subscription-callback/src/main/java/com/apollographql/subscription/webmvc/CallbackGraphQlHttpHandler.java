package com.apollographql.subscription.webmvc;

import static com.apollographql.subscription.callback.SubscriptionCallback.parseSubscriptionCallbackExtension;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER_VALUE;

import com.apollographql.subscription.callback.SubscriptionCallbackHandler;
import graphql.ExecutionResult;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jetbrains.annotations.NotNull;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.graphql.GraphQlRequest;
import org.springframework.graphql.ResponseError;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.support.SerializableGraphQlRequest;
import org.springframework.graphql.server.webmvc.GraphQlHttpHandler;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.AlternativeJdkIdGenerator;
import org.springframework.util.CollectionUtils;
import org.springframework.util.IdGenerator;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.HttpMediaTypeNotSupportedException;
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

  private final IdGenerator idGenerator = new AlternativeJdkIdGenerator();

  private final WebGraphQlHandler graphQlHandler;
  private final SubscriptionCallbackHandler subscriptionCallbackHandler;
  @Nullable private final HttpMessageConverter<Object> messageConverter;

  public CallbackGraphQlHttpHandler(WebGraphQlHandler graphQlHandler) {
    this(graphQlHandler, new SubscriptionCallbackHandler(graphQlHandler));
  }

  public CallbackGraphQlHttpHandler(
      WebGraphQlHandler graphQlHandler, SubscriptionCallbackHandler subscriptionCallbackHandler) {
    this(graphQlHandler, subscriptionCallbackHandler, null);
  }

  @SuppressWarnings("unchecked")
  public CallbackGraphQlHttpHandler(
      WebGraphQlHandler graphQlHandler,
      SubscriptionCallbackHandler subscriptionCallbackHandler,
      @Nullable HttpMessageConverter<?> converter) {
    super(graphQlHandler, converter);
    this.graphQlHandler = graphQlHandler;
    this.subscriptionCallbackHandler = subscriptionCallbackHandler;
    this.messageConverter = (HttpMessageConverter<Object>) converter;
  }

  @Override
  public @NotNull ServerResponse handleRequest(@NotNull ServerRequest serverRequest)
      throws ServletException {
    WebGraphQlRequest graphQlRequest =
        new WebGraphQlRequest(
            serverRequest.uri(),
            serverRequest.headers().asHttpHeaders(),
            initCookies(serverRequest),
            serverRequest.remoteAddress().orElse(null),
            serverRequest.attributes(),
            readBody(serverRequest),
            this.idGenerator.generateId().toString(),
            LocaleContextHolder.getLocale());

    if (logger.isDebugEnabled()) {
      logger.debug("Executing: " + graphQlRequest);
    }

    // in order to correctly handle parsing of ANY requests (i.e. it is valid to define a document
    // with query fragments first) we would need to parse it which is a much heavier operation, we
    // may opt to do it in the future releases
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
                      logger.error("Unable to start subscription using callback protocol", error);
                    }
                    return Mono.just(ServerResponse.badRequest().build());
                  });
      return ServerResponse.async(responseMono);
    } else {
      // regular GraphQL flow
      Mono<WebGraphQlResponse> responseMono =
          this.graphQlHandler
              .handleRequest(graphQlRequest)
              .doOnNext(
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
                  });
      return prepareResponse(serverRequest, responseMono);
    }
  }

  private Mono<WebGraphQlResponse> emptyResponse(WebGraphQlRequest request) {
    var emptyResponse = ExecutionResult.newExecutionResult().data(null).build();
    var emptyExecutionResponse =
        new DefaultExecutionGraphQlResponse(request.toExecutionInput(), emptyResponse);
    var emptyGraphQLResponse = new WebGraphQlResponse(emptyExecutionResponse);
    emptyGraphQLResponse
        .getResponseHeaders()
        .add(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE);
    return Mono.just(emptyGraphQLResponse);
  }

  // ========= CODE BELOW COPIED FROM SPRING =============
  // Those are all private static methods so sadly we cannot access them directly :(
  // copy from AbstractGraphQlHttpHandler
  private static MultiValueMap<String, HttpCookie> initCookies(ServerRequest serverRequest) {
    MultiValueMap<String, Cookie> source = serverRequest.cookies();
    MultiValueMap<String, HttpCookie> target = new LinkedMultiValueMap<>(source.size());
    source
        .values()
        .forEach(
            (cookieList) ->
                cookieList.forEach(
                    (cookie) -> {
                      HttpCookie httpCookie = new HttpCookie(cookie.getName(), cookie.getValue());
                      target.add(cookie.getName(), httpCookie);
                    }));
    return target;
  }

  // copy from AbstractGraphQlHttpHandler
  private GraphQlRequest readBody(ServerRequest request) throws ServletException {
    try {
      if (this.messageConverter != null) {
        MediaType contentType = request.headers().contentType().orElse(MediaType.APPLICATION_JSON);
        if (this.messageConverter.canRead(SerializableGraphQlRequest.class, contentType)) {
          ServerHttpRequest httpRequest = new ServletServerHttpRequest(request.servletRequest());
          return (GraphQlRequest)
              this.messageConverter.read(SerializableGraphQlRequest.class, httpRequest);
        }
        throw new HttpMediaTypeNotSupportedException(
            contentType, this.messageConverter.getSupportedMediaTypes(), request.method());
      } else {
        try {
          return request.body(SerializableGraphQlRequest.class);
        } catch (HttpMediaTypeNotSupportedException ex) {
          return applyApplicationGraphQlFallback(request, ex);
        }
      }
    } catch (IOException ex) {
      throw new ServerWebInputException("I/O error while reading request body", null, ex);
    }
  }

  // copy from AbstractGraphQlHttpHandler
  private static SerializableGraphQlRequest applyApplicationGraphQlFallback(
      ServerRequest request, HttpMediaTypeNotSupportedException ex)
      throws HttpMediaTypeNotSupportedException {

    // Spec requires application/json but some clients still use application/graphql
    if ("application/graphql".equals(request.headers().firstHeader(HttpHeaders.CONTENT_TYPE))) {
      try {
        request =
            ServerRequest.from(request)
                .headers((headers) -> headers.setContentType(MediaType.APPLICATION_JSON))
                .body(request.body(byte[].class))
                .build();
        return request.body(SerializableGraphQlRequest.class);
      } catch (Throwable ex2) {
        // ignore
      }
    }
    throw ex;
  }
}
