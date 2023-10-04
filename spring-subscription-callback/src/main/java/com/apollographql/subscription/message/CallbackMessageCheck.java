package com.apollographql.subscription.message;

/**
 * <code>check</code> message is used during initialization to ensure server can send callbacks
 * successfully, and that the <code>id</code> and <code>verifier</code> fields provided by
 * <strong>Router</strong> are correct.<br>
 * As long as subscription is active, server must send a <code>check</code> message to
 * <strong>Router</strong> every five seconds. This is used to confirm both that it can still reach
 * Router's callback endpoint, and that subscription is still active.
 *
 * @param id unique subscription ID
 * @param verifier value provided by Router that is used to validate requests
 */
public record CallbackMessageCheck(String id, String verifier)
    implements SubscritionCallbackMessage {

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
