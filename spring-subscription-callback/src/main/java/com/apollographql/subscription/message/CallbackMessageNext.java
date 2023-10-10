package com.apollographql.subscription.message;

import java.util.Map;

/**
 * <code>next</code> message contains emitted GraphQL subscription data.
 *
 * @param id unique subscription ID
 * @param verifier value provided by Router that is used to validate requests
 * @param payload emitted GraphQL subscription data
 */
public record CallbackMessageNext(String id, String verifier, Map<String, Object> payload)
    implements SubscritionCallbackMessage {

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
