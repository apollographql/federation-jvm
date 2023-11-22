package com.apollographql.subscription.callback;

import com.apollographql.subscription.exception.CallbackExtensionNotSpecifiedException;
import com.apollographql.subscription.exception.InvalidCallbackExtensionException;
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
 */
public record SubscriptionCallback(
    @NotNull String callback_url,
    @NotNull String subscription_id,
    @NotNull String verifier,
    int heartbeatIntervalMs) {

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
    var subscription_extension = extensions.get("subscription");
    if (subscription_extension instanceof Map subscription) {
      var callback_url = subscription.get("callback_url");
      var subscription_id = subscription.get("subscription_id");
      var verifier = subscription.get("verifier");
      var heartbeatMs = parseHeartbeats(subscription.get("heartbeat_interval_ms"));

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
      }
    }
    return -1;
  }
}
