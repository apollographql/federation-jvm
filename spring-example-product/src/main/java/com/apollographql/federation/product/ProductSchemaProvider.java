package com.apollographql.federation.product;

import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava._Entity;
import com.coxautodev.graphql.tools.SchemaParser;
import graphql.servlet.config.DefaultGraphQLSchemaProvider;
import graphql.servlet.config.GraphQLSchemaProvider;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ProductSchemaProvider extends DefaultGraphQLSchemaProvider implements GraphQLSchemaProvider {

    public static List<Product> products = new ArrayList<>();

    public ProductSchemaProvider(SchemaParser schemaParser) throws IOException {
        super(Federation.transform(schemaParser.makeExecutableSchema())
                .fetchEntities(env -> env.<List<Map<String, Object>>>getArgument(_Entity.argumentName)
                        .stream()
                        .map(values -> {
                            if ("Product".equals(values.get("__typename"))) {
                                final Object upc = values.get("upc");
                                if (upc instanceof String) {
                                    return lookupProduct((String) upc);
                                }
                            }
                            return null;
                        })
                        .collect(Collectors.toList()))
                .resolveEntityType(env -> {
                    final Object src = env.getObject();
                    if (src instanceof Product) {
                        return env.getSchema().getObjectType("Product");
                    }
                    return null;
                })
                .build());

        products.add(new Product("1", "Table", 899, 100));
        products.add(new Product("2", "Couch", 1299, 54));
        products.add(new Product("3", "Chair", 54, 50));
    }

    @NotNull
    private static Product lookupProduct(@NotNull String upc) {
        return products.stream().filter(product -> product.getUpc().equals(upc)).findAny().get();
    }
}
