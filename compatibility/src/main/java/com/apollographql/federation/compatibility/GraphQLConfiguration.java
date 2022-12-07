package com.apollographql.federation.compatibility;

import com.apollographql.federation.compatibility.model.DeprecatedProduct;
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
                                final String typeName = (String)reference.get("__typename");
                                return switch (typeName) {
                                    case "DeprecatedProduct" -> DeprecatedProduct.resolveReference(reference);
                                    case "Product" -> Product.resolveReference(reference);
                                    case "ProductResearch" -> ProductResearch.resolveReference(reference);
                                    case "User" -> User.resolveReference(reference);
                                    default -> null;
                                };
                            }).collect(Collectors.toList())
                        )
                        .resolveEntityType(env -> {
                            final Object src = env.getObject();
                            if (src instanceof DeprecatedProduct) {
                                return env.getSchema().getObjectType("DeprecatedProduct");
                            } else if (src instanceof Product) {
                                return env.getSchema().getObjectType("Product");
                            } else if (src instanceof ProductResearch) {
                                return env.getSchema().getObjectType("ProductResearch");
                            } else if (src instanceof User) {
                                return env.getSchema().getObjectType("User");
                            } else {
                                return null;
                            }
                        })
                        .build()
        );
    }
}