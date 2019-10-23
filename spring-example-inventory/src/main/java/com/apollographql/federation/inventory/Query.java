package com.apollographql.federation.inventory;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Service;

@Service
public class Query implements GraphQLQueryResolver {

    public Product trivial(final DataFetchingEnvironment dataFetchingEnvironment) {
        return new Product("123", 123);
    }
}
