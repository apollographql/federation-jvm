package com.apollographql.federation.review;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Service;

@Service
public class Query implements GraphQLQueryResolver {

    public Review trivia(DataFetchingEnvironment dataFetchingEnvironment) {
        return new Review();
    }
}
