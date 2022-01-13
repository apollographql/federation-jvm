package com.apollographql.federation.springexample.graphqljava;

import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava._Entity;
import com.apollographql.federation.graphqljava.caching.CacheControlInstrumentation;
import com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation;
import graphql.schema.GraphQLSchema;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.Resource;

@Configuration
@Profile("graphql-java")
public class AppConfiguration {
  @Bean
  public GraphQLSchema graphQLSchema(
      @Value("classpath:schemas/graphql-java/inventory.graphql") Resource sdl) throws IOException {
    return Federation.transform(sdl.getFile())
        .fetchEntities(
            env ->
                env.<List<Map<String, Object>>>getArgument(_Entity.argumentName).stream()
                    .map(
                        reference -> {
                          if ("Product".equals(reference.get("__typename"))) {
                            return Product.resolveReference(reference);
                          }
                          return null;
                        })
                    .collect(Collectors.toList()))
        .resolveEntityType(
            env -> {
              final Object src = env.getObject();
              if (src instanceof Product) {
                return env.getSchema().getObjectType("Product");
              }
              return null;
            })
        .build();
  }

  @Bean
  public FederatedTracingInstrumentation federatedTracingInstrumentation() {
    return new FederatedTracingInstrumentation(new FederatedTracingInstrumentation.Options(true));
  }

  @Bean
  public CacheControlInstrumentation cacheControlInstrumentation() {
    return new CacheControlInstrumentation();
  }
}
