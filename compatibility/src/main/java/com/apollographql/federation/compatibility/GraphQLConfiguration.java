package com.apollographql.federation.compatibility;

import com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.data.federation.FederationSchemaFactory;

@Configuration
public class GraphQLConfiguration {

  @Bean
  public FederatedTracingInstrumentation federatedTracingInstrumentation() {
    return new FederatedTracingInstrumentation();
  }

  @Bean
  public GraphQlSourceBuilderCustomizer customizer(FederationSchemaFactory factory) {
    return builder -> builder.schemaFactory(factory::createGraphQLSchema);
  }

  @Bean
  FederationSchemaFactory federationSchemaFactory() {
    return new FederationSchemaFactory();
  }
}
