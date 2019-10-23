package com.apollographql.federation.review;

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
public class ReviewSchemaProvider extends DefaultGraphQLSchemaProvider implements GraphQLSchemaProvider {
    public static List<Review> reviews = new ArrayList<>();
    public static List<User> users = new ArrayList<>();
    public ReviewSchemaProvider(SchemaParser schemaParser) throws IOException {
        super(Federation.transform(schemaParser.makeExecutableSchema())
                .fetchEntities(env -> env.<List<Map<String, Object>>>getArgument(_Entity.argumentName)
                        .stream()
                        .map(values -> {
                            if ("User".equals(values.get("__typename"))) {
                                final Object id = values.get("id");
                                if (id instanceof String) {
                                    return lookupUser((String) id);
                                }
                            }
                            return null;
                        })
                        .collect(Collectors.toList()))
                .resolveEntityType(env -> {
                    final Object src = env.getObject();
                    if (src instanceof User) {
                        return env.getSchema().getObjectType("User");
                    }
                    return null;
                })
                .build());
        users.add(new User("1", "@ada"));
        users.add(new User("2", "@complete"));
        reviews.add(new Review("1","Love it!", new User("1"), new Product("1")));
        reviews.add(new Review("2","Too expensive.", new User("1"), new Product("2")));
        reviews.add(new Review("3","Could be better.", new User("2"), new Product("3")));
        reviews.add(new Review("4","Prefer something else.", new User("2"), new Product("1")));
    }

    @NotNull
    private static User lookupUser(@NotNull String id) {
        User user1 = users.stream().filter(user -> user.getId().equals(id)).findAny().get();
        user1.setReviews(reviews.stream()
                .filter(review -> review.getAuthor().getId().equals(user1.getId()))
                .collect(Collectors.toList()));
        return user1;
    }
}
