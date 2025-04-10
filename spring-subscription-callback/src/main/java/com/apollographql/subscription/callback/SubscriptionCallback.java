package com.apollographql.subscription.callback;

import com.apollographql.subscription.exception.CallbackExtensionNotSpecifiedException;
import com.apollographql.subscription.exception.InvalidCallbackExtensionException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

/**
 * POJO representation of a subscription callback extension data.
 *
 * @param callback_url The URL that Emitter will send subscription data to
 * @param subscription_id The generated unique ID for the subscription operation
 * @param verifier A string that Emitter will include in all HTTP callback requests to verify its
 *     identity
 * @param heartbeatIntervalMs Interval between heartbeats in milliseconds
 * @param context Contextual data that should be returned with callbacks as HTTP headers
 */
public record SubscriptionCallback(
    @NotNull String callback_url,
    @NotNull String subscription_id,
    @NotNull String verifier,
    int heartbeatIntervalMs,
    @NotNull Map<String, List<String>> context) {

  public SubscriptionCallback(
      @NotNull String callback_url,
      @NotNull String subscription_id,
      @NotNull String verifier,
      int heartbeatIntervalMs) {
    this(callback_url, subscription_id, verifier, heartbeatIntervalMs, new HashMap<>());
  }

  @NotNull
  public SubscriptionCallback withContext(@NotNull Map<String, List<String>> context) {
    return new SubscriptionCallback(
        callback_url, subscription_id, verifier, heartbeatIntervalMs, context);
  }

  public static String SUBSCRIPTION_EXTENSION = "subscription";
  public static String CALLBACK_URL = "callbackUrl";
  public static String SUBSCRIPTION_ID = "subscriptionId";
  public static String VERIFIER = "verifier";
  public static String HEARTBEAT_INTERVAL_MS = "heartbeatIntervalMs";

  // added for backwards compatibility with non GA callback protocol
  // will be removed in next major release
  @Deprecated private static int DEFAULT_HEARTBEAT_INTERVAL_MS = 5000;
  @Deprecated private static String DEPRECATED_CALLBACK_URL = "callback_url";
  @Deprecated private static String DEPRECATED_SUBSCRIPTION_ID = "subscription_id";
  @Deprecated private static String DEPRECATED_HEARTBEAT_INTERVAL_MS = "heartbeat_interval_ms";

  /**
   * Parse subscription callback information from GraphQL request extension.
   *
   * @param extensions GraphQL request extensions element
   * @return Mono containing parsed `SubscriptionCallback` extension data or an exception if
   *     extension is not specified or is malformed.
   */
  @SuppressWarnings("unchecked")
  @NotNull
  public static Mono<SubscriptionCallback> parseSubscriptionCallbackExtension(
      @NotNull Map<String, Object> extensions) {
    var subscription_extension = extensions.get(SUBSCRIPTION_EXTENSION);
    if (subscription_extension instanceof Map subscription) {
      var callback_url = subscription.get(CALLBACK_URL);
      if (callback_url == null) {
        callback_url = subscription.get(DEPRECATED_CALLBACK_URL);
      }
      var subscription_id = subscription.get(SUBSCRIPTION_ID);
      if (subscription_id == null) {
        subscription_id = subscription.get(DEPRECATED_SUBSCRIPTION_ID);
      }
      var verifier = subscription.get(VERIFIER);
      var rawHeartbeatMs = subscription.get(HEARTBEAT_INTERVAL_MS);
      if (rawHeartbeatMs == null) {
        rawHeartbeatMs = subscription.get(DEPRECATED_HEARTBEAT_INTERVAL_MS);
      }
      var heartbeatMs = parseHeartbeats(rawHeartbeatMs);

      if (callback_url != null && subscription_id != null && verifier != null && heartbeatMs >= 0) {
        return Mono.just(
            new SubscriptionCallback(
                (String) callback_url, (String) subscription_id, (String) verifier, heartbeatMs));
      } else {
        return Mono.error(new InvalidCallbackExtensionException(subscription));
      }
    }
    return Mono.error(new CallbackExtensionNotSpecifiedException());
  }

  private static int parseHeartbeats(Object heartbeatMs) {
    if (heartbeatMs != null) {
      try {
        return (Integer) heartbeatMs;
      } catch (ClassCastException e) {
        // heartbeat_interval_ms is not an integer
        return -1;
      }
    }
    return DEFAULT_HEARTBEAT_INTERVAL_MS;
  }
}
