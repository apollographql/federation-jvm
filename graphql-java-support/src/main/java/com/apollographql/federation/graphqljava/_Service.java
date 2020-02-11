package com.apollographql.federation.graphqljava;

import com.apollographql.federation.graphqljava.misc.Constants;
import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;

import static graphql.schema.GraphQLFieldDefinition.newFieldDefinition;
import static graphql.schema.GraphQLObjectType.newObject;

final class _Service {

    static final GraphQLObjectType type = newObject()
            .name(Constants.SERVICE_TYPE_NAME)
            .field(newFieldDefinition()
                    .name(Constants.SDL_FIELD_NAME)
                    .type(new GraphQLNonNull(Scalars.GraphQLString))
                    .build())
            .build();

    static final GraphQLFieldDefinition field = newFieldDefinition()
            .name(Constants.SERVICE_FIELD_NAME)
            .type(type)
            .build();

    private _Service() {
    }
}
