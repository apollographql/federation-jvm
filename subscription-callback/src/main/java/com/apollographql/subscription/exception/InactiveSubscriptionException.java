package com.apollographql.subscription.exception;

import com.apollographql.subscription.callback.SubscriptionCallback;

public class InactiveSubscriptionException extends RuntimeException {

  public InactiveSubscriptionException(SubscriptionCallback callback) {
    super("Callback protocol " + callback + " failed to communicate with router");
  }
}
