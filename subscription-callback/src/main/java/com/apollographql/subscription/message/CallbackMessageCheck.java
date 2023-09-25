package com.apollographql.subscription.message;

public record CallbackMessageCheck(String id, String verifier) implements SubscritionCallbackMessage {

  @Override
  public String getKind() {
    return "subscription";
  }

  @Override
  public CallbackMessageAction getAction() {
    return CallbackMessageAction.CHECK;
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
