package com.apollographql.subscription.message;

import graphql.GraphQLError;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <code>complete</code> message is send to the <strong>Router</strong> to terminate active
 * subscription. Subscription can terminate either by reaching end of the data stream OR by
 * encountering an error that caused subscription to fail.<br>
 * If subscription failed due to an error, <code>complete</code> message should include list of
 * <code>GraphQLError</code>s that caused the failure.
 *
 * @param id unique subscription ID
 * @param verifier value provided by Router that is used to validate requests
 * @param errors optional list of errors if subscription terminated abnormally
 */
public record CallbackMessageComplete(String id, String verifier, List<GraphQLError> errors)
    implements SubscritionCallbackMessage {

  public CallbackMessageComplete(String id, String verifier) {
    this(id, verifier, Collections.emptyList());
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

  public List<Map<String, Object>> getErrors() {
    return errors.stream().map(GraphQLError::toSpecification).collect(Collectors.toList());
  }
}
