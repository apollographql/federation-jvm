package com.apollographql.subscription.exception;

import com.apollographql.subscription.callback.SubscriptionCallback;

/** Exception thrown when callback initialization fails. */
public class CallbackInitializationFailedException extends RuntimeException {

  public CallbackInitializationFailedException(SubscriptionCallback callback, int statusCode) {
    super(
        "Subscription callback failed initialization: "
            + callback
            + ", server responded with: "
            + statusCode);
  }
}
