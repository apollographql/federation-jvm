package com.apollographql.federation.graphqljava;

import com.apollographql.federation.graphqljava.misc.Constants;
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

    private _Entity() {
    }

    // graphql-java will mutate GraphQLTypeReference in-place,
    // so we need to create a new instance every time.
    static GraphQLFieldDefinition field(@NotNull Set<String> typeNames) {
        return newFieldDefinition()
                .name(Constants.ENTITIES_FIELD_NAME)
                .argument(newArgument()
                        .name(Constants.ARGUMENT_NAME)
                        .type(new GraphQLNonNull(new GraphQLList(new GraphQLNonNull(new GraphQLTypeReference(Constants.ANY_TYPE_NAME)))))
                        .build())
                .type(new GraphQLNonNull(
                                new GraphQLList(
                                        GraphQLUnionType.newUnionType()
                                                .name(Constants.ENTITY_TYPE_NAME)
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
