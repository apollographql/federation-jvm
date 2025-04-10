package com.apollographql.subscription;

import static com.apollographql.subscription.callback.SubscriptionCallback.parseSubscriptionCallbackExtension;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER;
import static com.apollographql.subscription.callback.SubscriptionCallbackHandler.SUBSCRIPTION_PROTOCOL_HEADER_VALUE;

import com.apollographql.subscription.callback.SubscriptionCallbackHandler;
import graphql.ExecutionResult;
import graphql.GraphQLError;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.Ordered;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.support.DefaultExecutionGraphQlResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/**
 * Interceptor that provides support for Apollo Subscription Callback Protocol. HTTP headers from
 * the original subscription GraphQL operation can be propagated to the subsequent callback
 * responses by specifying a Set of contextual header names. This interceptor defaults to {@link
 * Ordered#LOWEST_PRECEDENCE} order as it should run last in the chain to allow users to still apply
 * other interceptors that handle common stuff (e.g. extracting auth headers, etc). You can override
 * this behavior by specifying custom order.
 *
 * @see <a
 *     href="https://www.apollographql.com/docs/graphos/routing/operations/subscriptions/callback-protocol">Subscription
 *     Callback Protocol</a>
 */
public class CallbackWebGraphQLInterceptor implements WebGraphQlInterceptor, Ordered {

  private static final Log logger = LogFactory.getLog(CallbackWebGraphQLInterceptor.class);

  private final SubscriptionCallbackHandler subscriptionCallbackHandler;
  private final int order;
  private final Set<String> contextualHeaders;

  public CallbackWebGraphQLInterceptor(SubscriptionCallbackHandler subscriptionCallbackHandler) {
    this(subscriptionCallbackHandler, LOWEST_PRECEDENCE);
  }

  public CallbackWebGraphQLInterceptor(
      SubscriptionCallbackHandler subscriptionCallbackHandler, int order) {
    this(subscriptionCallbackHandler, order, new HashSet<>());
  }

  public CallbackWebGraphQLInterceptor(
      SubscriptionCallbackHandler subscriptionCallbackHandler, Set<String> contextualHeaders) {
    this(subscriptionCallbackHandler, LOWEST_PRECEDENCE, contextualHeaders);
  }

  public CallbackWebGraphQLInterceptor(
      SubscriptionCallbackHandler subscriptionCallbackHandler,
      int order,
      Set<String> contextualHeaders) {
    this.subscriptionCallbackHandler = subscriptionCallbackHandler;
    this.order = order;
    this.contextualHeaders = contextualHeaders;
  }

  @Override
  public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
    // in order to correctly handle parsing of ANY requests (i.e. it is valid to define a
    // document with query fragments first) we would need to parse it which is a much heavier
    // operation, we may opt to do it in the future releases
    if (!isWebSocketRequest(request) && request.getDocument().startsWith("subscription")) {
      return parseSubscriptionCallbackExtension(request.getExtensions())
          .flatMap(
              callback -> {
                if (logger.isDebugEnabled()) {
                  logger.debug("Starting subscription using callback: " + callback);
                }
                var callbackWithContextualData =
                    callback.withContext(parseContextualHeaders(request));
                return this.subscriptionCallbackHandler
                    .handleSubscriptionUsingCallback(request, callbackWithContextualData)
                    .map(response -> callbackResponse(request, response));
              })
          .onErrorResume(
              (error) -> {
                if (logger.isErrorEnabled()) {
                  logger.error("Unable to start subscription using callback protocol", error);
                }
                return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST));
              });
    } else {
      return chain.next(request);
    }
  }

  private boolean isWebSocketRequest(WebGraphQlRequest request) {
    return request instanceof WebSocketGraphQlRequest;
  }

  private WebGraphQlResponse callbackResponse(
      WebGraphQlRequest request, ExecutionResult callbackResult) {
    var callbackExecutionResponse =
        new DefaultExecutionGraphQlResponse(request.toExecutionInput(), callbackResult);
    var callbackGraphQLResponse = new WebGraphQlResponse(callbackExecutionResponse);
    callbackGraphQLResponse
        .getResponseHeaders()
        .add(SUBSCRIPTION_PROTOCOL_HEADER, SUBSCRIPTION_PROTOCOL_HEADER_VALUE);
    return callbackGraphQLResponse;
  }

  private WebGraphQlResponse errorCallbackResponse(WebGraphQlRequest request) {
    var errorCallbackResult =
        ExecutionResult.newExecutionResult()
            .addError(
                GraphQLError.newError()
                    .message("Unable to start subscription using callback protocol")
                    .build())
            .build();
    return callbackResponse(request, errorCallbackResult);
  }

  @Override
  public int getOrder() {
    return order;
  }

  private Map<String, List<String>> parseContextualHeaders(WebGraphQlRequest request) {
    if (!this.contextualHeaders.isEmpty()) {
      Map<String, List<String>> contextualHeaders = new HashMap<>();
      var headers = request.getHeaders();
      for (String header : this.contextualHeaders) {
        if (headers.containsKey(header)) {
          var headerValue = request.getHeaders().get(header);
          contextualHeaders.put(header, headerValue);
        }
      }
      return contextualHeaders;
    } else {
      return new HashMap<>();
    }
  }
}
