package com.apollographql.subscription.message;

import java.util.Map;

public record CallbackMessageNext(String id, String verifier, Map<String, Object> payload) implements SubscritionCallbackMessage {

  @Override
  public String getKind() {
    return "subscription";
  }

  @Override
  public CallbackMessageAction getAction() {
    return CallbackMessageAction.NEXT;
  }

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getVerifier() {
    return verifier;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }
}
