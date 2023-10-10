package com.apollographql.subscription.message;

/** Common interface for HTTP callback messages. */
public sealed interface SubscritionCallbackMessage
    permits CallbackMessageCheck, CallbackMessageNext, CallbackMessageComplete {
  default String getKind() {
    return "subscription";
  }

  CallbackMessageAction getAction();

  String getId();

  String getVerifier();
}
