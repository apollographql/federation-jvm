package com.apollographql.subscription;

import java.util.List;

public record SubscriptionCallbackResponse(String id, List<String> invalid_ids, String verifier) {
}
