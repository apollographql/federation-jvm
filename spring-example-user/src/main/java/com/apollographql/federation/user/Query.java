package com.apollographql.federation.user;

import com.coxautodev.graphql.tools.GraphQLQueryResolver;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Service;

@Service
public class Query implements GraphQLQueryResolver {

    private final UserService userService;

    public Query(final UserService userService) {
        this.userService = userService;
    }

    public User me (Integer userId, final DataFetchingEnvironment dataFetchingEnvironment) {
        User user = userService.lookupUser(String.valueOf(userId));
        return user;
    }
}
