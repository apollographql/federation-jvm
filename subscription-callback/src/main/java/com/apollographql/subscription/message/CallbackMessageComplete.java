package com.apollographql.subscription.message;

import graphql.GraphQLError;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record CallbackMessageComplete(String id, String verifier, List<GraphQLError> errors)
    implements SubscritionCallbackMessage {

  public CallbackMessageComplete(String id, String verifier) {
    this(id, verifier, Collections.emptyList());
  }

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

  public List<Map<String, Object>> getErrors() {
    return errors.stream().map(GraphQLError::toSpecification).collect(Collectors.toList());
  }
}
