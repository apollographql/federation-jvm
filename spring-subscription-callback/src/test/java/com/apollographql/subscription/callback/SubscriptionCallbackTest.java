package com.apollographql.subscription.callback;

import static com.apollographql.subscription.callback.SubscriptionCallback.CALLBACK_URL;
import static com.apollographql.subscription.callback.SubscriptionCallback.HEARTBEAT_INTERVAL_MS;
import static com.apollographql.subscription.callback.SubscriptionCallback.SUBSCRIPTION_EXTENSION;
import static com.apollographql.subscription.callback.SubscriptionCallback.SUBSCRIPTION_ID;
import static com.apollographql.subscription.callback.SubscriptionCallback.VERIFIER;

import com.apollographql.subscription.exception.CallbackExtensionNotSpecifiedException;
import com.apollographql.subscription.exception.InvalidCallbackExtensionException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

public class SubscriptionCallbackTest {

  @Test
  public void callback_valid() {
    var expected = new SubscriptionCallback("foo.com", "1234567890", "junit", 1000);
    Map<String, Object> extension =
        Map.of(
            SUBSCRIPTION_EXTENSION,
            Map.of(
                CALLBACK_URL, expected.callback_url(),
                SUBSCRIPTION_ID, expected.subscription_id(),
                VERIFIER, expected.verifier(),
                HEARTBEAT_INTERVAL_MS, expected.heartbeatIntervalMs()));
    var callback = SubscriptionCallback.parseSubscriptionCallbackExtension(extension);
    StepVerifier.create(callback).expectNext(expected).verifyComplete();
  }

  @Test
  public void callback_missingExtension_returnsError() {
    var callback = SubscriptionCallback.parseSubscriptionCallbackExtension(Map.of());
    StepVerifier.create(callback)
        .expectError(CallbackExtensionNotSpecifiedException.class)
        .verify();
  }

  @Test
  public void callback_missingCallbackUrl_returnsError() {
    var callback =
        SubscriptionCallback.parseSubscriptionCallbackExtension(
            Map.of(
                SUBSCRIPTION_EXTENSION,
                Map.of(
                    SUBSCRIPTION_ID, "123",
                    VERIFIER, "junit",
                    HEARTBEAT_INTERVAL_MS, 1000)));
    StepVerifier.create(callback).expectError(InvalidCallbackExtensionException.class).verify();
  }

  @Test
  public void callback_missingHeartbeat_defaults5s() {
    var expected = new SubscriptionCallback("foo.com", "1234567890", "junit", 5000);
    Map<String, Object> extension =
        Map.of(
            SUBSCRIPTION_EXTENSION,
            Map.of(
                CALLBACK_URL, expected.callback_url(),
                SUBSCRIPTION_ID, expected.subscription_id(),
                VERIFIER, expected.verifier()));
    var callback = SubscriptionCallback.parseSubscriptionCallbackExtension(extension);
    StepVerifier.create(callback).expectNext(expected).verifyComplete();
  }

  @Test
  public void callback_nonIntegerHeartbeat_returnsError() {
    var callback =
        SubscriptionCallback.parseSubscriptionCallbackExtension(
            Map.of(
                SUBSCRIPTION_EXTENSION,
                Map.of(
                    CALLBACK_URL, "foo.com",
                    SUBSCRIPTION_ID, "123",
                    VERIFIER, "junit",
                    HEARTBEAT_INTERVAL_MS, "100")));
    StepVerifier.create(callback).expectError(InvalidCallbackExtensionException.class).verify();
  }

  @Test
  public void callback_negativeHeartbeat_returnsError() {
    var callback =
        SubscriptionCallback.parseSubscriptionCallbackExtension(
            Map.of(
                SUBSCRIPTION_EXTENSION,
                Map.of(
                    CALLBACK_URL, "foo.com",
                    SUBSCRIPTION_ID, "123",
                    VERIFIER, "junit",
                    HEARTBEAT_INTERVAL_MS, -100)));
    StepVerifier.create(callback).expectError(InvalidCallbackExtensionException.class).verify();
  }

  @Test
  public void callback_usingSnakeCase_valid() {
    var expected = new SubscriptionCallback("foo.com", "1234567890", "junit", 1000);
    Map<String, Object> extension =
        Map.of(
            SUBSCRIPTION_EXTENSION,
            Map.of(
                "callback_url",
                expected.callback_url(),
                "subscription_id",
                expected.subscription_id(),
                VERIFIER,
                expected.verifier(),
                "heartbeat_interval_ms",
                expected.heartbeatIntervalMs()));
    var callback = SubscriptionCallback.parseSubscriptionCallbackExtension(extension);
    StepVerifier.create(callback).expectNext(expected).verifyComplete();
  }
}
