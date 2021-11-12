package com.apollographql.federation.springexample.graphqljavatools;

import com.apollographql.federation.graphqljava.SchemaTransformer;
import com.apollographql.federation.graphqljava._Entity;
import com.apollographql.federation.graphqljava.caching.CacheControlInstrumentation;
import com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation;
import graphql.schema.GraphQLSchema;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("graphql-java-tools")
public class AppConfiguration {
  @Bean
  public GraphQLSchema graphQLSchema(SchemaTransformer schemaTransformer) {
    return schemaTransformer
        .fetchEntities(
            env ->
                env.<List<Map<String, Object>>>getArgument(_Entity.argumentName).stream()
                    .map(
                        reference -> {
                          if ("Product".equals(reference.get("__typename"))) {
                            return ProductReferenceResolver.resolveReference(reference);
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
