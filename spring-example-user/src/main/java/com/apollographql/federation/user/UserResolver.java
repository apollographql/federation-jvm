package com.apollographql.federation.user;

import com.coxautodev.graphql.tools.GraphQLResolver;
import graphql.schema.DataFetchingEnvironment;
import org.springframework.stereotype.Service;

@Service
public class UserResolver implements GraphQLResolver<User> {

    private final UserService userService;

    public UserResolver(final UserService userService) {
        this.userService = userService;
    }

    public Address address(User user, DataFetchingEnvironment dataFetchingEnvironment) {
        return userService.findAddressByUserId(user.getId());
    }
}
