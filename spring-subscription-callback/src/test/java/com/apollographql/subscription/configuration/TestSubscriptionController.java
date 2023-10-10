package com.apollographql.subscription.configuration;

import java.time.Duration;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

@Controller
public class TestSubscriptionController {
  @QueryMapping
  public String hello() {
    return "Hello World!";
  }

  @SubscriptionMapping
  public Flux<Integer> counter() {
    return Flux.just(1, 2, 3).delayElements(Duration.ofMillis(10));
  }
}
