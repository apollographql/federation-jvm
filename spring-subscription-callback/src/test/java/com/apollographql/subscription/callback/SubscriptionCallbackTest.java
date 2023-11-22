package com.apollographql.subscription.callback;

import com.apollographql.subscription.exception.CallbackExtensionNotSpecifiedException;
import com.apollographql.subscription.exception.InvalidCallbackExtensionException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.test.StepVerifier;

public class SubscriptionCallbackTest {

  @Test
  public void callback_valid() {
    var expected = new SubscriptionCallback("foo.com", "1234567890", "junit", 5000);
    Map<String, Object> extension =
        Map.of(
            "subscription",
            Map.of(
                "callback_url", expected.callback_url(),
                "subscription_id", expected.subscription_id(),
                "verifier", expected.verifier(),
                "heartbeat_interval_ms", expected.heartbeatIntervalMs()));
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
                "subscription",
                Map.of(
                    "subscription_id", "123",
                    "verifier", "junit",
                    "heartbeat_interval_ms", 1000)));
    StepVerifier.create(callback).expectError(InvalidCallbackExtensionException.class).verify();
  }

  @Test
  public void callback_nonIntegerHeartbeat_returnsError() {
    var callback =
        SubscriptionCallback.parseSubscriptionCallbackExtension(
            Map.of(
                "subscription",
                Map.of(
                    "callback_url", "foo.com",
                    "subscription_id", "123",
                    "verifier", "junit",
                    "heartbeat_interval_ms", "100")));
    StepVerifier.create(callback).expectError(InvalidCallbackExtensionException.class).verify();
  }

  @Test
  public void callback_negativeHeartbeat_returnsError() {
    var callback =
        SubscriptionCallback.parseSubscriptionCallbackExtension(
            Map.of(
                "subscription",
                Map.of(
                    "callback_url", "foo.com",
                    "subscription_id", "123",
                    "verifier", "junit",
                    "heartbeat_interval_ms", -100)));
    StepVerifier.create(callback).expectError(InvalidCallbackExtensionException.class).verify();
  }
}
