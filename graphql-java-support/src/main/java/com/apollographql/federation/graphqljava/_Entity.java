package com.apollographql.federation.graphqljava;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLTypeReference;
import graphql.schema.GraphQLUnionType;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

import static graphql.schema.GraphQLArgument.newArgument;
import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;

public final class _Entity {
    public static final String argumentName = "representations";
    static final String typeName = "_Entity";
    static final String fieldName = "_entities";

    private _Entity() {
    }

    // graphql-java will mutate GraphQLTypeReference in-place,
    // so we need to create a new instance every time.
    static GraphQLFieldDefinition field(@NotNull Set<String> typeNames) {
        return newFieldDefinition()
                .name(fieldName)
                .argument(newArgument()
                        .name(argumentName)
                        .type(new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(new GraphQLTypeReference(_Any.typeName)))))
                        .build())
                .type(new GraphQLNonNull(
                                new GraphQLList(
                                        GraphQLUnionType.newUnionType()
                                                .name(typeName)
                                                .possibleTypes(typeNames.stream()
                                                        .map(GraphQLTypeReference::new)
                                                        .toArray(GraphQLTypeReference[]::new))
                                                .build()
                                )
                        )
                )
                .build();
    }
}
