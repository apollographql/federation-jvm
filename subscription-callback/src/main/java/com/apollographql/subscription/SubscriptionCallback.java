package com.apollographql.subscription;

public record SubscriptionCallback(String callback_url, String subscription_id, String verifier) {}
