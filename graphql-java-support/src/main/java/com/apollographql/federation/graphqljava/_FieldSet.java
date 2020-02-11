package com.apollographql.federation.graphqljava;

import com.apollographql.federation.graphqljava.misc.Constants;
import graphql.Scalars;
import graphql.language.ScalarTypeDefinition;
import graphql.schema.GraphQLScalarType;

public final class _FieldSet {

    public static GraphQLScalarType type = GraphQLScalarType.newScalar(Scalars.GraphQLString)
            .name(Constants.FIELD_SET_TYPE_NAME)
            .coercing(Scalars.GraphQLString.getCoercing())
            .build();

    public static final ScalarTypeDefinition definition = ScalarTypeDefinition.newScalarTypeDefinition()
            .name(Constants.FIELD_SET_TYPE_NAME)
            .build();
}
