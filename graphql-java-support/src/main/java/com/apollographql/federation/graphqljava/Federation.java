package com.apollographql.federation.graphqljava;

import graphql.schema.GraphQLSchema;
import org.jetbrains.annotations.NotNull;

public final class Federation {
    @NotNull
    public static com.apollographql.federation.graphqljava.SchemaTransformer transform(final GraphQLSchema schema) {
        return new SchemaTransformer(schema);
    }

    private Federation() {
    }
}
