package com.apollographql.federation.graphqljava;

import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Reader;

public final class Federation {
    private static final SchemaGenerator.Options generatorOptions = SchemaGenerator.Options.defaultOptions()
            .enforceSchemaDirectives(false);

    private Federation() {
    }

    @NotNull
    public static SchemaTransformer transform(final GraphQLSchema schema) {
        return new SchemaTransformer(() -> schema);
    }

    public static SchemaTransformer transform(final TypeDefinitionRegistry typeRegistry, final RuntimeWiring runtimeWiring) {
        final GraphQLSchema original = new SchemaGenerator().makeExecutableSchema(
                generatorOptions,
                typeRegistry,
                runtimeWiring);
        return transform(original);
    }

    public static SchemaTransformer transform(final TypeDefinitionRegistry typeRegistry) {
        return transform(typeRegistry, RuntimeWiring.newRuntimeWiring().build());
    }

    public static SchemaTransformer transform(final String sdl) {
        return transform(new SchemaParser().parse(sdl));
    }

    public static SchemaTransformer transform(final Reader sdl) {
        return transform(new SchemaParser().parse(sdl));
    }

    public static SchemaTransformer transform(final File sdl) {
        return transform(new SchemaParser().parse(sdl));
    }
}
