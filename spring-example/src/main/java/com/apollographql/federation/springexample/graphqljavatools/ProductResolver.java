package com.apollographql.federation.springexample.graphqljavatools;

import graphql.kickstart.tools.GraphQLResolver;
import org.springframework.stereotype.Component;

@Component
public class ProductResolver implements GraphQLResolver<Product> {
    public boolean isInStock(Product product) {
        return product.getQuantity() > 0;
    }
}
