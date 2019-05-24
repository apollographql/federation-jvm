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

final class _Entity {
    static final String typeName = "_Entity";
    static final String fieldName = "_entities";
    static final String argumentName = "representations";

    static GraphQLFieldDefinition field = newFieldDefinition()
            .name(fieldName)
            .argument(newArgument()
                    .name(argumentName)
                    .type(new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(_Any.type))))
                    .build())
            .type(new GraphQLNonNull(new GraphQLList(new GraphQLTypeReference(typeName))))
            .build();

    static GraphQLUnionType build(@NotNull Set<String> typeNames) {
        final GraphQLTypeReference[] references = typeNames.stream()
                .map(GraphQLTypeReference::new)
                .toArray(GraphQLTypeReference[]::new);
        return GraphQLUnionType.newUnionType()
                .name(typeName)
                .possibleTypes(references)
                .build();
    }

    private _Entity() {
    }
}
