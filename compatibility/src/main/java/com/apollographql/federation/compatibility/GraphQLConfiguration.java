package com.apollographql.federation.compatibility;

import com.apollographql.federation.compatibility.model.DeprecatedProduct;
import com.apollographql.federation.compatibility.model.Inventory;
import com.apollographql.federation.compatibility.model.Product;
import com.apollographql.federation.compatibility.model.ProductResearch;
import com.apollographql.federation.compatibility.model.User;
import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava._Entity;
import com.apollographql.federation.graphqljava.tracing.FederatedTracingInstrumentation;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.graphql.GraphQlSourceBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.ClassNameTypeResolver;

@Configuration
public class GraphQLConfiguration {

  @Bean
  public FederatedTracingInstrumentation federatedTracingInstrumentation() {
    return new FederatedTracingInstrumentation();
  }

  @Bean
  public GraphQlSourceBuilderCustomizer federationTransform() {
    return builder -> builder.schemaFactory((registry, wiring) ->
      Federation.transform(registry, wiring)
        .fetchEntities(env ->
          env.<List<Map<String, Object>>>getArgument(_Entity.argumentName).stream().map(reference -> {
            final String typeName = (String) reference.get("__typename");
            return switch (typeName) {
              case "DeprecatedProduct" -> DeprecatedProduct.resolveReference(reference);
              case "Product" -> Product.resolveReference(reference);
              case "ProductResearch" -> ProductResearch.resolveReference(reference);
              case "User" -> User.resolveReference(reference);
              case "Inventory" -> Inventory.resolveReference(reference);
              default -> null;
            };
          }).collect(Collectors.toList())
        )
        .resolveEntityType(new ClassNameTypeResolver())
        .build()
    );
  }
}
