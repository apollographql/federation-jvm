package com.apollographql.subscription.message;
public sealed interface SubscritionCallbackMessage permits CallbackMessageCheck, CallbackMessageHeartbeat, CallbackMessageNext, CallbackMessageComplete {
  String getKind(); //  default "subscription";

  CallbackMessageAction getAction();

  String getId();

  String getVerifier();
}

