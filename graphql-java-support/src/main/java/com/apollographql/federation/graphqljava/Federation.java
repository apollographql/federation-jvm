package com.apollographql.federation.graphqljava;

import graphql.language.ObjectTypeDefinition;
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
        return new SchemaTransformer(schema);
    }

    public static SchemaTransformer transform(final TypeDefinitionRegistry typeRegistry, final RuntimeWiring runtimeWiring) {
        ensureQueryTypeExists(typeRegistry);
        final GraphQLSchema original = new SchemaGenerator().makeExecutableSchema(
                generatorOptions,
                typeRegistry,
                runtimeWiring);
        return transform(original);
    }

    public static SchemaTransformer transform(final TypeDefinitionRegistry typeRegistry) {
        return transform(typeRegistry, emptyWiring());
    }

    public static SchemaTransformer transform(final String sdl, final RuntimeWiring runtimeWiring) {
        return transform(new SchemaParser().parse(sdl), runtimeWiring);
    }

    public static SchemaTransformer transform(final Reader sdl, final RuntimeWiring runtimeWiring) {
        return transform(new SchemaParser().parse(sdl), runtimeWiring);
    }

    public static SchemaTransformer transform(final File sdl, final RuntimeWiring runtimeWiring) {
        return transform(new SchemaParser().parse(sdl), runtimeWiring);
    }

    public static SchemaTransformer transform(final String sdl) {
        return transform(sdl, emptyWiring());
    }

    public static SchemaTransformer transform(final Reader sdl) {
        return transform(sdl, emptyWiring());
    }

    public static SchemaTransformer transform(final File sdl) {
        return transform(sdl, emptyWiring());
    }

    private static RuntimeWiring emptyWiring() {
        return RuntimeWiring.newRuntimeWiring().build();
    }

    private static void ensureQueryTypeExists(TypeDefinitionRegistry typeRegistry) {
        final String queryName = typeRegistry.schemaDefinition()
                .flatMap(sdef -> sdef.getOperationTypeDefinitions()
                        .stream()
                        .filter(op -> "query".equals(op.getName()))
                        .findFirst()
                        .map(def -> def.getTypeName().getName()))
                .orElse("Query");
        if (!typeRegistry.getType(queryName).isPresent()) {
            typeRegistry.add(ObjectTypeDefinition.newObjectTypeDefinition().name(queryName).build());
        }
    }
}
