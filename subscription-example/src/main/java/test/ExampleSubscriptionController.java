package test;

import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SubscriptionMapping;
import org.springframework.stereotype.Controller;
import reactor.core.publisher.Flux;

@Controller
public class ExampleSubscriptionController {

  private static Map<Integer, Review> REVIEWS =
      Stream.of(
              new Review(1, "foo", new Product("1")),
              new Review(2, "bar", new Product("1")),
              new Review(3, "baz", new Product("1")),
              new Review(4, "foobar", new Product("2")),
              new Review(5, "foobar", new Product("2")),
              new Review(6, "foobar", new Product("2")),
              new Review(7, "foobar", new Product("1")),
              new Review(8, "foobar", new Product("3")),
              new Review(9, "foobar", new Product("3")),
              new Review(10, "foobar", new Product("1")),
              new Review(11, "foobar", new Product("3")),
              new Review(12, "foobar", new Product("2")),
              new Review(13, "foobar", new Product("2")))
          .collect(Collectors.toMap(Review::id, r -> r));

  @QueryMapping
  public Review review(@Argument int id) {
    return REVIEWS.get(id);
  }

  @SubscriptionMapping
  public Flux<Review> reviewAdded() {
    return Flux.fromIterable(REVIEWS.values())
        .doOnEach(r -> System.out.println("Emitting " + r))
        .delayElements(Duration.ofMillis(1000));
  }
}
