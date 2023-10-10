package com.apollographql.subscription;

import java.util.HashMap;
import java.util.Map;

public class CallbackTestUtils {
  public static Map<String, Object> createMockGraphQLRequest(
      String subscriptionId, String callbackUrl) {
    var subscriptionExtension = new HashMap<String, Object>();
    subscriptionExtension.put("callback_url", callbackUrl);
    subscriptionExtension.put("subscription_id", subscriptionId);
    subscriptionExtension.put("verifier", "junit");
    return Map.of(
        "query",
        "subscription { counter }",
        "extensions",
        Map.of("subscription", subscriptionExtension));
  }
}
