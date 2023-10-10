package com.apollographql.subscription.exception;

/**
 * Exception thrown when user attempts to execute subscription through POST route without specifying
 * callback extension.
 */
public class CallbackExtensionNotSpecifiedException extends RuntimeException {

  public CallbackExtensionNotSpecifiedException() {
    super("Callback protocol not specified, subscription using POST request is not supported");
  }
}
