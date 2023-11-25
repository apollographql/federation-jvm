package com.apollographql.subscription;

import static com.apollographql.subscription.callback.SubscriptionCallback.CALLBACK_URL;
import static com.apollographql.subscription.callback.SubscriptionCallback.HEARTBEAT_INTERVAL_MS;
import static com.apollographql.subscription.callback.SubscriptionCallback.SUBSCRIPTION_EXTENSION;
import static com.apollographql.subscription.callback.SubscriptionCallback.SUBSCRIPTION_ID;
import static com.apollographql.subscription.callback.SubscriptionCallback.VERIFIER;

import java.util.HashMap;
import java.util.Map;

public class CallbackTestUtils {
  public static Map<String, Object> createMockGraphQLRequest(
      String subscriptionId, String callbackUrl) {
    var subscriptionExtension = new HashMap<String, Object>();
    subscriptionExtension.put(CALLBACK_URL, callbackUrl);
    subscriptionExtension.put(SUBSCRIPTION_ID, subscriptionId);
    subscriptionExtension.put(VERIFIER, "junit");
    subscriptionExtension.put(HEARTBEAT_INTERVAL_MS, 5000);
    return Map.of(
        "query",
        "subscription { counter }",
        "extensions",
        Map.of(SUBSCRIPTION_EXTENSION, subscriptionExtension));
  }
}
