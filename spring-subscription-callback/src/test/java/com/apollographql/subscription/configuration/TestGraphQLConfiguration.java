package com.apollographql.subscription.configuration;

import org.springframework.boot.graphql.autoconfigure.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.graphql.data.federation.FederationSchemaFactory;

public class TestGraphQLConfiguration {

  @Bean
  public GraphQlSourceBuilderCustomizer customizer(FederationSchemaFactory factory) {
    return builder -> builder.schemaFactory(factory::createGraphQLSchema);
  }

  @Bean
  public FederationSchemaFactory federationSchemaFactory() {
    return new FederationSchemaFactory();
  }
}
