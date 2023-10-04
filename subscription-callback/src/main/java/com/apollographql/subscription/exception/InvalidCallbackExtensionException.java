package com.apollographql.subscription.exception;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Exception thrown when callback extension data is malformed (missing entries or have null values).
 */
public class InvalidCallbackExtensionException extends RuntimeException {

  public InvalidCallbackExtensionException(Map<String, Object> callbackExtension) {
    super(
        String.format(
            "Invalid callback protocol extension specified, extension %s",
            callbackExtension.entrySet().stream()
                .map(mapper -> mapper.getKey() + "=" + mapper.getValue())
                .collect(Collectors.joining(", ", "{", "}"))));
  }
}
