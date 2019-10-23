package com.apollographql.federation.product;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class Query implements GraphQLQueryResolver {

    public List<Product> topProducts(Integer first, final DataFetchingEnvironment dataFetchingEnvironment) {
        return ProductSchemaProvider.products.stream().limit(first).collect(Collectors.toList());
    }
}
