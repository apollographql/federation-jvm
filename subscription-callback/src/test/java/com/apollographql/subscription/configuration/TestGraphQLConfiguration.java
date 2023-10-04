package com.apollographql.subscription.configuration;

import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava._Entity;
import graphql.schema.DataFetcher;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.execution.ClassNameTypeResolver;

public class TestGraphQLConfiguration {
  // configure federation
  @Bean
  public GraphQlSourceBuilderCustomizer federationTransform() {
    DataFetcher<?> entityDataFetcher =
        env -> {
          List<Map<String, Object>> representations = env.getArgument(_Entity.argumentName);
          return representations.stream().map(representation -> null).collect(Collectors.toList());
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
