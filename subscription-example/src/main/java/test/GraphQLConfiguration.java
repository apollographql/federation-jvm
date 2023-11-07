package test;

import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava._Entity;
import com.apollographql.subscription.callback.SubscriptionCallbackHandler;
import com.apollographql.subscription.webmvc.CallbackGraphQlHttpHandler;
import graphql.schema.DataFetcher;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.ClassNameTypeResolver;
import org.springframework.graphql.server.WebGraphQlHandler;
import org.springframework.graphql.server.webmvc.GraphQlHttpHandler;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
public class GraphQLConfiguration {

  @Bean
  public GraphQlHttpHandler graphQlHttpHandler(WebGraphQlHandler webGraphQlHandler) {
    Scheduler customScheduler = Schedulers.immediate();
    SubscriptionCallbackHandler subscriptionHandler =
        new SubscriptionCallbackHandler(webGraphQlHandler, customScheduler);
    return new CallbackGraphQlHttpHandler(webGraphQlHandler, subscriptionHandler);
  }

  @Bean
  public GraphQlSourceBuilderCustomizer federationTransform() {
    DataFetcher<?> entityDataFetcher =
        env -> {
          List<Map<String, Object>> representations = env.getArgument(_Entity.argumentName);
          return representations.stream()
              .map(
                  representation -> {
                    if ("Product".equals(representation.get("__typename"))) {
                      return new Product((String) representation.get("id"));
                    }
                    return null;
                  })
              .collect(Collectors.toList());
        };

    return builder ->
        builder.schemaFactory(
            (registry, wiring) ->
                Federation.transform(registry, wiring)
                    .fetchEntities(entityDataFetcher)
                    .resolveEntityType(new ClassNameTypeResolver())
                    .build());
  }
}
