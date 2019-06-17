package com.apollographql.federation.graphqljava;

import graphql.Scalars;
import graphql.schema.GraphQLScalarType;

final class _FieldSet {

    static final String typeName = "_FieldSet";

    static GraphQLScalarType type = GraphQLScalarType.newScalar(Scalars.GraphQLString)
                    .name(typeName)
                    .coercing(Scalars.GraphQLString.getCoercing())
                    .build();
}
