package com.apollographql.federation.springexample;

import com.apollographql.federation.graphqljava.Federation;
import com.apollographql.federation.graphqljava._Entity;
import graphql.servlet.config.DefaultGraphQLSchemaProvider;
import graphql.servlet.config.GraphQLSchemaProvider;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class InventorySchemaProvider extends DefaultGraphQLSchemaProvider implements GraphQLSchemaProvider {
    public InventorySchemaProvider(@Value("classpath:schemas/inventory.graphql") Resource sdl) throws IOException {
        super(Federation.transform(sdl.getFile())
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
    }

    @NotNull
    private static Product lookupProduct(@NotNull String upc) {
        try {
            // Why not?
            int quantity = Math.floorMod(
                    new BigInteger(1,
                            MessageDigest.getInstance("SHA1").digest(upc.getBytes())
                    ).intValue(),
                    10_000);

            return new Product(upc, quantity);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
