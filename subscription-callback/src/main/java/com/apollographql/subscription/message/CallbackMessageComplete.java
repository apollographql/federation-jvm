package com.apollographql.subscription.message;

public record CallbackMessageComplete(String id, String verifier) implements SubscritionCallbackMessage {

  @Override
  public String getKind() {
    return "subscription";
  }

  @Override
  public CallbackMessageAction getAction() {
    return CallbackMessageAction.COMPLETE;
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
