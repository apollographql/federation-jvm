package com.apollographql.subscription.message;

public record CallbackMessageHeartbeat(String id, String verifier) implements SubscritionCallbackMessage {

  @Override
  public String getKind() {
    return "subscription";
  }

  @Override
  public CallbackMessageAction getAction() {
    return CallbackMessageAction.HEARTBEAT;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getVerifier() {
    return verifier;
  }
}
