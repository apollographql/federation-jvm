package com.apollographql.subscription.exception;

import com.apollographql.subscription.callback.SubscriptionCallback;

/**
 * Exception thrown when subscription becomes inactive (router responds using 2xx status to any
 * callback message).
 */
public class InactiveSubscriptionException extends RuntimeException {

  public InactiveSubscriptionException(SubscriptionCallback callback) {
    super("Callback protocol " + callback + " failed to communicate with router");
  }
}
