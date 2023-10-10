package com.apollographql.subscription.message;

import com.fasterxml.jackson.annotation.JsonProperty;

/** HTTP callback message type */
public enum CallbackMessageAction {
  @JsonProperty("check")
  CHECK,
  @JsonProperty("next")
  NEXT,
  @JsonProperty("complete")
  COMPLETE
}
