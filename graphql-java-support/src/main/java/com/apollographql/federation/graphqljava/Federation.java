package com.apollographql.federation.graphqljava;

import graphql.language.DirectiveDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Reader;
import java.util.Comparator;

public final class Federation {
    private static final SchemaGenerator.Options generatorOptions = SchemaGenerator.Options.defaultOptions()
            .enforceSchemaDirectives(true);

    private Federation() {
    }

    @NotNull
    public static SchemaTransformer transform(final GraphQLSchema schema) {
        return new SchemaTransformer(schema);
    }

    public static SchemaTransformer transform(final TypeDefinitionRegistry typeRegistry, final RuntimeWiring runtimeWiring) {
        ensureQueryTypeExists(typeRegistry);
        RuntimeWiring newRuntimeWiring = ensureFederationDirectiveDefinitionsExist(typeRegistry, runtimeWiring);
        final GraphQLSchema original = new SchemaGenerator().makeExecutableSchema(
                generatorOptions,
                typeRegistry,
                newRuntimeWiring);
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

    private static RuntimeWiring ensureFederationDirectiveDefinitionsExist(
            TypeDefinitionRegistry typeRegistry,
            RuntimeWiring runtimeWiring
    ) {
        // Add Federation directives if they don't exist.
        FederationDirectives.allDefinitions
                .stream()
                .filter(def -> !typeRegistry.getDirectiveDefinition(def.getName()).isPresent())
                .forEachOrdered(typeRegistry::add);

        // Add scalar type for _FieldSet, since the directives depend on it.
        if (!typeRegistry.getType(_FieldSet.typeName).isPresent()) {
            typeRegistry.add(_FieldSet.definition);
        }

        // Also add the implementation for _FieldSet.
        if (!runtimeWiring.getScalars().containsKey(_FieldSet.typeName)) {
            return copyRuntimeWiring(runtimeWiring).scalar(_FieldSet.type).build();
        } else {
            return runtimeWiring;
        }
    }

    private static RuntimeWiring.Builder copyRuntimeWiring(RuntimeWiring runtimeWiring) {
        // Annoyingly graphql-java doesn't have a copy constructor for RuntimeWiring.Builder.
        RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();

        runtimeWiring.getDataFetchers()
                .entrySet()
                .stream()
                .map(entry -> {
                    String name = entry.getKey();
                    TypeRuntimeWiring.Builder typeWiring = TypeRuntimeWiring.newTypeWiring(name);
                    typeWiring.dataFetchers(entry.getValue());
                    if (runtimeWiring.getDefaultDataFetcherForType(name) != null) {
                      typeWiring.defaultDataFetcher(runtimeWiring.getDefaultDataFetcherForType(name));
                    }
                    if (runtimeWiring.getTypeResolvers().get(name) != null) {
                        typeWiring.typeResolver(runtimeWiring.getTypeResolvers().get(name));
                    }
                    if (runtimeWiring.getEnumValuesProviders().get(name) != null) {
                        typeWiring.enumValues(runtimeWiring.getEnumValuesProviders().get(name));
                    }
                    return typeWiring.build();
                })
                .forEach(builder::type);

        if (runtimeWiring.getWiringFactory() != null) {
            builder.wiringFactory(runtimeWiring.getWiringFactory());
        }
        if (runtimeWiring.getCodeRegistry() != null) {
            builder.codeRegistry(runtimeWiring.getCodeRegistry());
        }
        runtimeWiring.getScalars().forEach((name, scalar) -> builder.scalar(scalar));
        if (runtimeWiring.getFieldVisibility() != null) {
            builder.fieldVisibility(runtimeWiring.getFieldVisibility());
        }
        runtimeWiring.getRegisteredDirectiveWiring().forEach(builder::directive);
        runtimeWiring.getDirectiveWiring().forEach(builder::directiveWiring);
        builder.comparatorRegistry(runtimeWiring.getComparatorRegistry());
        runtimeWiring.getSchemaGeneratorPostProcessings().forEach(builder::transformer);

        return builder;
    }
}
