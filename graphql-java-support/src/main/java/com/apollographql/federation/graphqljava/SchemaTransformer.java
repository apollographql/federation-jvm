package com.apollographql.federation.graphqljava;

import static com.apollographql.federation.graphqljava.printer.ServiceSDLPrinter.generateServiceSDL;
import static com.apollographql.federation.graphqljava.printer.ServiceSDLPrinter.generateServiceSDLV2;

import graphql.GraphQLError;
import graphql.schema.Coercing;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactory;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.TypeResolver;
import graphql.schema.idl.errors.SchemaProblem;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public final class SchemaTransformer {
  private static final Object serviceObject = new Object();
  private final GraphQLSchema originalSchema;
  private final boolean queryTypeShouldBeEmpty;
  private TypeResolver entityTypeResolver = null;
  private DataFetcher entitiesDataFetcher = null;
  private DataFetcherFactory entitiesDataFetcherFactory = null;
  private Coercing coercingForAny = _Any.defaultCoercing;
  private boolean isFederation2 = false;

  SchemaTransformer(GraphQLSchema originalSchema, boolean queryTypeShouldBeEmpty) {
    this.originalSchema = originalSchema;
    this.queryTypeShouldBeEmpty = queryTypeShouldBeEmpty;
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

  public SchemaTransformer setFederation2(boolean isFederation2) {
    this.isFederation2 = isFederation2;
    return this;
  }

  @NotNull
  public final GraphQLSchema build() throws SchemaProblem {
    final List<GraphQLError> errors = new ArrayList<>();

    // Make new Schema
    final GraphQLSchema.Builder newSchema = GraphQLSchema.newSchema(originalSchema);

    final GraphQLObjectType originalQueryType = originalSchema.getQueryType();

    final GraphQLObjectType.Builder newQueryType = GraphQLObjectType.newObject(originalQueryType);
    if (queryTypeShouldBeEmpty) newQueryType.clearFields();
    newQueryType.field(_Service.field);

    final Set<String> entityTypeNames = getFederatedEntities();
    // If there are entity types install: Query._entities(representations: [_Any!]!): [_Entity]!
    if (!entityTypeNames.isEmpty()) {
      newQueryType.field(_Entity.field(entityTypeNames));

      final GraphQLType originalAnyType = originalSchema.getType(_Any.typeName);
      if (originalAnyType == null) {
        newSchema.additionalType(_Any.type(coercingForAny));
      }
    }
    newSchema.query(newQueryType.build());

    final GraphQLCodeRegistry.Builder newCodeRegistry =
        GraphQLCodeRegistry.newCodeRegistry(originalSchema.getCodeRegistry());

    if (!entityTypeNames.isEmpty()) {
      if (entityTypeResolver != null) {
        newCodeRegistry.typeResolver(_Entity.typeName, entityTypeResolver);
      } else {
        if (!newCodeRegistry.hasTypeResolver(_Entity.typeName)) {
          errors.add(new FederationError("Missing a type resolver for _Entity"));
        }
      }

      final FieldCoordinates _entities =
          FieldCoordinates.coordinates(originalQueryType.getName(), _Entity.fieldName);
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

    // expose the schema as _service.sdl
    newCodeRegistry.dataFetcher(
        FieldCoordinates.coordinates(originalQueryType.getName(), _Service.fieldName),
        (DataFetcher<Object>) environment -> serviceObject);
    final String sdl;
    if (isFederation2) {
      sdl = generateServiceSDLV2(newSchema.codeRegistry(newCodeRegistry.build()).build());
    } else {
      // For Federation1, we filter out the federation definitions
      sdl = sdl(originalSchema, queryTypeShouldBeEmpty);
    }
    newCodeRegistry.dataFetcher(
        FieldCoordinates.coordinates(_Service.typeName, _Service.sdlFieldName),
        (DataFetcher<String>) environment -> sdl);

    return newSchema.codeRegistry(newCodeRegistry.build()).build();
  }

  /**
   * Find all federated entities in the given GraphQLSchema.
   *
   * @return Set containing all federated entity type names.
   */
  Set<String> getFederatedEntities() {
    final Set<String> entitiesWithExplicitKeys =
        originalSchema.getAllTypesAsList().stream()
            .filter(entityPredicate())
            .map(GraphQLNamedType::getName)
            .collect(Collectors.toSet());

    return originalSchema.getAllTypesAsList().stream()
        .filter(entityObjectPredicate(entitiesWithExplicitKeys))
        .map(GraphQLNamedType::getName)
        .collect(Collectors.toSet());
  }

  /**
   * Check against GraphQLNamedType to determine whether it is a Federated entity type (including
   * interfaces).
   *
   * @return true <em>iff</em> type contains `@key` directive
   */
  private Predicate<GraphQLNamedType> entityPredicate() {
    return type -> {
      if (type instanceof GraphQLDirectiveContainer) {
        GraphQLDirectiveContainer entityCandidate = (GraphQLDirectiveContainer) type;
        return entityCandidate
                .getAllAppliedDirectivesByName()
                .containsKey(FederationDirectives.keyName)
            || entityCandidate.getAllDirectivesByName().containsKey(FederationDirectives.keyName);
      } else {
        return false;
      }
    };
  }

  /**
   * Check whether GraphQLObjectType specifies `@key` directive OR implements an interface that
   * specifies `@key` directive.
   *
   * @return true <em>iff</em> GraphQL object is federated entity type.
   */
  private Predicate<GraphQLNamedType> entityObjectPredicate(Set<String> entityNames) {
    return type -> {
      if (type instanceof GraphQLObjectType) {
        GraphQLObjectType objectType = (GraphQLObjectType) type;
        return entityNames.contains(objectType.getName())
            || objectType.getInterfaces().stream()
                .anyMatch(interfaceType -> entityNames.contains(interfaceType.getName()));
      } else {
        return false;
      }
    };
  }

  /**
   * Generate Apollo Federation v1 compatible SDL that should be returned from `_service { sdl }` query.
   *
   * @param schema target schema
   * @deprecated use ServiceSDLPrinter instead
   * @return SDL compatible with Apollo Federation v1
   */
  @Deprecated()
  public static String sdl(GraphQLSchema schema) {
    return sdl(schema, false);
  }

  /**
   * Generate Apollo Federation v1 compatible SDL that should be returned from `_service { sdl }` query.
   *
   * @param schema target schema
   * @param queryTypeShouldBeEmpty boolean flag indicating whether schema contains dummy query that should be removed
   * @deprecated use ServiceSDLPrinter instead
   * @return SDL compatible with Apollo Federation v1
   */
  @Deprecated
  public static String sdl(GraphQLSchema schema, boolean queryTypeShouldBeEmpty) {
    return generateServiceSDL(schema, queryTypeShouldBeEmpty);
  }
}
