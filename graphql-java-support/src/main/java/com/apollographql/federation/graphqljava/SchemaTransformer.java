package com.apollographql.federation.graphqljava;

import graphql.GraphQLError;
import graphql.schema.Coercing;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetcherFactory;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLDirectiveContainer;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.TypeResolver;
import graphql.schema.idl.errors.SchemaProblem;
import graphql.schema.visibility.GraphqlFieldVisibility;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

public final class SchemaTransformer {
  private static final Object serviceObject = new Object();
  // Apollo Gateway will fail composition if it sees standard directive definitions.
  private static final Set<String> STANDARD_DIRECTIVES =
      new HashSet<>(Arrays.asList("deprecated", "include", "skip", "specifiedBy"));
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
      // For federation2, we're trying something new and outputing
      final Set<String> standardDirectives =
          new HashSet<>(Arrays.asList("deprecated", "include", "skip", "specifiedBy"));

      sdl =
          new FederationSdlPrinter(
                  FederationSdlPrinter.Options.defaultOptions()
                      .includeScalarTypes(true)
                      .includeDirectiveDefinitions(
                          def -> !standardDirectives.contains(def.getName())))
              .print(newSchema.codeRegistry(newCodeRegistry.build()).build())
              .trim();
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
    final Set<String> entityTypeNames =
        originalSchema.getAllTypesAsList().stream()
            .filter(entityPredicate())
            .map(GraphQLNamedType::getName)
            .collect(Collectors.toSet());

    return originalSchema.getAllTypesAsList().stream()
        .filter(entityObjectPredicate(entityTypeNames))
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

  public static String sdl(GraphQLSchema schema) {
    return sdl(schema, false);
  }

  public static String sdl(GraphQLSchema schema, boolean queryTypeShouldBeEmpty) {
    // Gather directive definitions to hide.
    final Set<String> hiddenDirectiveDefinitions = new HashSet<>();
    hiddenDirectiveDefinitions.addAll(STANDARD_DIRECTIVES);
    hiddenDirectiveDefinitions.addAll(FederationDirectives.allNames);

    // Gather type definitions to hide.
    final Set<String> hiddenTypeDefinitions = new HashSet<>();
    hiddenTypeDefinitions.add(_Any.typeName);
    hiddenTypeDefinitions.add(_Entity.typeName);
    hiddenTypeDefinitions.add(_FieldSet.typeName);
    hiddenTypeDefinitions.add(_Service.typeName);

    // Change field visibility for the query type if needed.
    if (queryTypeShouldBeEmpty) {
      final String queryTypeName = schema.getQueryType().getName();
      final GraphqlFieldVisibility oldFieldVisibility =
          schema.getCodeRegistry().getFieldVisibility();
      final GraphqlFieldVisibility newFieldVisibility =
          new GraphqlFieldVisibility() {
            @Override
            public List<GraphQLFieldDefinition> getFieldDefinitions(
                GraphQLFieldsContainer fieldsContainer) {
              return fieldsContainer.getName().equals(queryTypeName)
                  ? Collections.emptyList()
                  : oldFieldVisibility.getFieldDefinitions(fieldsContainer);
            }

            @Override
            public GraphQLFieldDefinition getFieldDefinition(
                GraphQLFieldsContainer fieldsContainer, String fieldName) {
              return fieldsContainer.getName().equals(queryTypeName)
                  ? null
                  : oldFieldVisibility.getFieldDefinition(fieldsContainer, fieldName);
            }
          };
      final GraphQLCodeRegistry newCodeRegistry =
          schema
              .getCodeRegistry()
              .transform(
                  codeRegistryBuilder -> codeRegistryBuilder.fieldVisibility(newFieldVisibility));
      schema = schema.transform(schemaBuilder -> schemaBuilder.codeRegistry(newCodeRegistry));
    }

    // Note that FederationSdlPrinter is a copy of graphql-java's SchemaPrinter that adds the
    // ability to filter out directive and type definitions, which is required by federation
    // spec.
    //
    // FederationSdlPrinter will need to be updated whenever graphql-java changes versions. It
    // can be removed when graphql-java adds native support for filtering out directive and
    // type definitions or federation spec changes to allow the currently forbidden directive
    // and type definitions.
    final FederationSdlPrinter.Options options =
        FederationSdlPrinter.Options.defaultOptions()
            .includeScalarTypes(true)
            .includeSchemaDefinition(true)
            .includeDirectives(true)
            .includeDirectiveDefinitions(def -> !hiddenDirectiveDefinitions.contains(def.getName()))
            .includeTypeDefinitions(def -> !hiddenTypeDefinitions.contains(def.getName()));
    return new FederationSdlPrinter(options).print(schema);
  }
}
