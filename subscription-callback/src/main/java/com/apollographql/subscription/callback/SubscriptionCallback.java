package com.apollographql.subscription.callback;

import java.util.Map;

public record SubscriptionCallback(String callback_url, String subscription_id, String verifier) {

  public static SubscriptionCallback parseSubscriptionCallbackExtension(Map<String, Object> extensions) {
    var subscription_extension = extensions.get("subscription");
    if (subscription_extension instanceof Map subscription) {
      var callback_url = subscription.get("callback_url");
      var subscription_id = subscription.get("subscription_id");
      var verifier = subscription.get("verifier");

      if (callback_url != null && subscription_id != null && verifier != null) {
        return new SubscriptionCallback((String) callback_url, (String) subscription_id, (String) verifier);
      } else {
        // throw invalid input?
      }
    }
    return null;
  }
}
