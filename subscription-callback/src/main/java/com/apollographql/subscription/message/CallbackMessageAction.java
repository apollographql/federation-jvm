package com.apollographql.subscription.message;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum CallbackMessageAction {
  @JsonProperty("check")
  CHECK,
  @JsonProperty("heartbeat")
  HEARTBEAT,
  @JsonProperty("next")
  NEXT,
  @JsonProperty("complete")
  COMPLETE
}
