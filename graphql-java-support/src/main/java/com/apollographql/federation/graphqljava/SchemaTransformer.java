package com.apollographql.federation.graphqljava;

import graphql.GraphQLError;
import graphql.schema.Coercing;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactory;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.TypeResolver;
import graphql.schema.idl.SchemaPrinter;
import graphql.schema.idl.errors.SchemaProblem;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SchemaTransformer {
    private static final Object DUMMY = new Object();
    private final GraphQLSchema originalSchema;
    private TypeResolver entityTypeResolver = null;
    private DataFetcher entitiesDataFetcher = null;
    private DataFetcherFactory entitiesDataFetcherFactory = null;
    private Coercing coercingForAny = _Any.defaultCoercing;

    SchemaTransformer(GraphQLSchema originalSchema) {
        this.originalSchema = originalSchema;
    }

    @NotNull
    public SchemaTransformer resolveEntityType(TypeResolver entityTypeResolver) {
        this.entityTypeResolver = entityTypeResolver;
        return this;
    }

    @NotNull
    public SchemaTransformer fetchEntities(DataFetcher entitiesDataFetcher) {
        this.entitiesDataFetcher = entitiesDataFetcher;
        this.entitiesDataFetcherFactory = null;
        return this;
    }

    @NotNull
    public SchemaTransformer fetchEntitiesFactory(DataFetcherFactory entitiesDataFetcherFactory) {
        this.entitiesDataFetcher = null;
        this.entitiesDataFetcherFactory = entitiesDataFetcherFactory;
        return this;
    }

    public SchemaTransformer coercingForAny(Coercing coercing) {
        this.coercingForAny = coercing;
        return this;
    }

    @NotNull
    public final GraphQLSchema build() throws SchemaProblem {
        final List<GraphQLError> errors = new ArrayList<>();

        final GraphQLSchema.Builder newSchema = GraphQLSchema.newSchema(originalSchema);

        final GraphQLObjectType originalQueryType = originalSchema.getQueryType();

        final GraphQLCodeRegistry.Builder newCodeRegistry =
                GraphQLCodeRegistry.newCodeRegistry(originalSchema.getCodeRegistry());

        final String sdl = sdl();
        final GraphQLObjectType.Builder newQueryType = GraphQLObjectType.newObject(originalQueryType)
                .field(_Service.field);
        newCodeRegistry.dataFetcher(FieldCoordinates.coordinates(
                originalQueryType.getName(),
                _Service.fieldName
                ),
                (DataFetcher<Object>) environment -> DUMMY);
        newCodeRegistry.dataFetcher(FieldCoordinates.coordinates(
                _Service.typeName,
                _Service.sdlFieldName
                ),
                (DataFetcher<String>) environment -> sdl);

        final Set<String> entityTypeNames = originalSchema.getAllTypesAsList().stream()
                .filter(t -> t instanceof GraphQLDirectiveContainer &&
                        ((GraphQLDirectiveContainer) t).getDirective(FederationDirectives.keyName) != null)
                .map(GraphQLType::getName)
                .collect(Collectors.toSet());

        final Set<String> entityConcreteTypeNames = originalSchema.getAllTypesAsList()
                .stream()
                .filter(type -> type instanceof GraphQLObjectType)
                .filter(type -> entityTypeNames.contains(type.getName()) ||
                        ((GraphQLObjectType) type).getInterfaces()
                                .stream()
                                .anyMatch(itf -> entityTypeNames.contains(itf.getName())))
                .map(GraphQLType::getName)
                .collect(Collectors.toSet());

        if (!entityConcreteTypeNames.isEmpty()) {
            newQueryType.field(_Entity.field(entityConcreteTypeNames));

            final GraphQLType originalAnyType = originalSchema.getType(_Any.typeName);
            if (originalAnyType == null) {
                newSchema.additionalType(_Any.type(coercingForAny));
            }

            if (entityTypeResolver != null) {
                newCodeRegistry.typeResolver(_Entity.typeName, entityTypeResolver);
            } else {
                if (!newCodeRegistry.hasTypeResolver(_Entity.typeName)) {
                    errors.add(new FederationError("Missing a type resolver for _Entity"));
                }
            }

            final FieldCoordinates _entities = FieldCoordinates.coordinates(originalQueryType.getName(), _Entity.fieldName);
            if (entitiesDataFetcher != null) {
                newCodeRegistry.dataFetcher(_entities, entitiesDataFetcher);
            } else if (entitiesDataFetcherFactory != null) {
                newCodeRegistry.dataFetcher(_entities, entitiesDataFetcherFactory);
            } else if (!newCodeRegistry.hasDataFetcher(_entities)) {
                errors.add(new FederationError("Missing a data fetcher for _entities"));
            }
        }

        if (!errors.isEmpty()) {
            throw new SchemaProblem(errors);
        }

        return newSchema
                .query(newQueryType.build())
                .codeRegistry(newCodeRegistry.build())
                .build();
    }

    private String sdl() {
        final SchemaPrinter.Options options = SchemaPrinter.Options.defaultOptions()
                .includeScalarTypes(true)
                .includeExtendedScalarTypes(true)
                .includeSchemaDefintion(true)
                .includeDirectives(true);
        return new SchemaPrinter(options).print(originalSchema);
    }
}
