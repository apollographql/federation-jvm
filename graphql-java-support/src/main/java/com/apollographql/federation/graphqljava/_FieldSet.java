package com.apollographql.federation.graphqljava;

import graphql.Scalars;
import graphql.language.ScalarTypeDefinition;
import graphql.schema.GraphQLScalarType;

public final class _FieldSet {
    static final String typeName = "_FieldSet";

    public static GraphQLScalarType type = GraphQLScalarType.newScalar(Scalars.GraphQLString)
            .name(typeName)
            .coercing(Scalars.GraphQLString.getCoercing())
            .build();

    public static final ScalarTypeDefinition definition = ScalarTypeDefinition.newScalarTypeDefinition()
            .name(typeName)
            .build();
}
