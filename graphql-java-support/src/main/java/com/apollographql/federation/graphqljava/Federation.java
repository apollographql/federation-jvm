package com.apollographql.federation.graphqljava;

import static com.apollographql.federation.graphqljava.directives.LinkDirectiveProcessor.loadFederationImportedDefinitions;

import graphql.language.DirectiveDefinition;
import graphql.language.EnumTypeDefinition;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.SDLNamedDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import java.io.File;
import java.io.Reader;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;

public final class Federation {

  public static final String FEDERATION_SPEC_V2_0 = "https://specs.apollo.dev/federation/v2.0";
  public static final String FEDERATION_SPEC_V2_1 = "https://specs.apollo.dev/federation/v2.1";
  public static final String FEDERATION_SPEC_V2_2 = "https://specs.apollo.dev/federation/v2.2";
  public static final String FEDERATION_SPEC_V2_3 = "https://specs.apollo.dev/federation/v2.3";
  public static final String FEDERATION_SPEC_V2_4 = "https://specs.apollo.dev/federation/v2.4";
  public static final String FEDERATION_SPEC_V2_5 = "https://specs.apollo.dev/federation/v2.5";
  public static final String FEDERATION_SPEC_V2_6 = "https://specs.apollo.dev/federation/v2.6";
  public static final String FEDERATION_SPEC_V2_7 = "https://specs.apollo.dev/federation/v2.7";
  public static final String FEDERATION_SPEC_V2_8 = "https://specs.apollo.dev/federation/v2.8";
  public static final String FEDERATION_SPEC_V2_9 = "https://specs.apollo.dev/federation/v2.9";

  private static final SchemaGenerator.Options generatorOptions =
      SchemaGenerator.Options.defaultOptions();

  private Federation() {}

  @NotNull
  public static SchemaTransformer transform(final GraphQLSchema schema) {
    return new SchemaTransformer(schema, false);
  }

  // Note that GraphQLSchema does not support empty object types as of graphql-java v16. If you
  // would like the query type to be empty, then add a dummy field to the query type in the given
  // GraphQLSchema and pass queryTypeShouldBeEmpty as true. The output schema won't contain the
  // dummy field, nor will it be visible to the gateway.
  //
  // You can also use a transform() overload that accepts something other than a GraphQLSchema,
  // as those overloads do allow their inputs to specify an empty query type.
  @NotNull
  public static SchemaTransformer transform(
      final GraphQLSchema schema, final boolean queryTypeShouldBeEmpty) {
    return new SchemaTransformer(schema, queryTypeShouldBeEmpty);
  }

  public static SchemaTransformer transform(
      final TypeDefinitionRegistry typeRegistry, final RuntimeWiring runtimeWiring) {
    final boolean queryTypeShouldBeEmpty = ensureQueryTypeExists(typeRegistry);

    RuntimeWiring federatedRuntimeWiring;
    Stream<SDLNamedDefinition> importedDefinitions =
        loadFederationImportedDefinitions(typeRegistry);
    if (importedDefinitions != null) {
      federatedRuntimeWiring =
          ensureFederationV2DirectiveDefinitionsExist(
              typeRegistry, runtimeWiring, importedDefinitions);
    } else {
      federatedRuntimeWiring =
          ensureFederationDirectiveDefinitionsExist(typeRegistry, runtimeWiring);
    }
    final GraphQLSchema schema =
        new SchemaGenerator()
            .makeExecutableSchema(generatorOptions, typeRegistry, federatedRuntimeWiring);
    return transform(schema, queryTypeShouldBeEmpty).setFederation2(importedDefinitions != null);
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

  // Returns true if a dummy field was added to the query type to ensure it's not empty.
  private static boolean ensureQueryTypeExists(TypeDefinitionRegistry typeRegistry) {
    final String queryName =
        typeRegistry
            .schemaDefinition()
            .flatMap(
                sdef ->
                    sdef.getOperationTypeDefinitions().stream()
                        .filter(op -> "query".equals(op.getName()))
                        .findFirst()
                        .map(def -> def.getTypeName().getName()))
            .orElse("Query");
    TypeDefinition<?> newQueryType =
        typeRegistry
            .getType(queryName)
            .orElse(ObjectTypeDefinition.newObjectTypeDefinition().name(queryName).build());
    final boolean addDummyField =
        newQueryType instanceof ObjectTypeDefinition
            && ((ObjectTypeDefinition) newQueryType).getFieldDefinitions().isEmpty()
            && Optional.ofNullable(typeRegistry.objectTypeExtensions().get(queryName))
                // Note that an object type extension must have at least one field
                .map(List::isEmpty)
                .orElse(true);
    if (addDummyField) {
      newQueryType =
          ((ObjectTypeDefinition) newQueryType)
              .transform(
                  objectTypeDefinitionBuilder ->
                      objectTypeDefinitionBuilder.fieldDefinition(
                          FieldDefinition.newFieldDefinition()
                              .name("_dummy")
                              .type(new TypeName("String"))
                              .build()));
    }
    // Note that TypeDefinitionRegistry will throw if you attempt to redefine a type, but it
    // reacts fine if you try to remove a type that doesn't exist.
    typeRegistry.remove(newQueryType);
    typeRegistry.add(newQueryType);
    return addDummyField;
  }

  private static RuntimeWiring ensureFederationV2DirectiveDefinitionsExist(
      TypeDefinitionRegistry typeRegistry,
      RuntimeWiring runtimeWiring,
      Stream<SDLNamedDefinition> importedDefinitions) {

    final HashSet<GraphQLScalarType> scalarTypesToAdd = new HashSet<>();
    importedDefinitions.forEach(
        def -> {
          if (def instanceof DirectiveDefinition
              && typeRegistry.getDirectiveDefinition(def.getName()).isEmpty()) {
            typeRegistry.add(def);
          } else if (def instanceof ScalarTypeDefinition) {
            if (!typeRegistry.scalars().containsKey(def.getName())) {
              typeRegistry.add(def);
            }
            if (!runtimeWiring.getScalars().containsKey(def.getName())) {
              scalarTypesToAdd.add(
                  GraphQLScalarType.newScalar()
                      .name(def.getName())
                      .description(null)
                      .coercing(_Any.defaultCoercing)
                      .build());
            }
          } else if (def instanceof EnumTypeDefinition
              && !typeRegistry.types().containsKey(def.getName())) {
            typeRegistry.add(def);
          }
        });

    if (!scalarTypesToAdd.isEmpty()) {
      return runtimeWiring.transform((wiring) -> scalarTypesToAdd.forEach(wiring::scalar));
    } else {
      return runtimeWiring;
    }
  }

  private static RuntimeWiring ensureFederationDirectiveDefinitionsExist(
      TypeDefinitionRegistry typeRegistry, RuntimeWiring runtimeWiring) {

    // Add Federation directives if they don't exist.
    FederationDirectives.federation1DirectiveDefinitions.stream()
        .filter(def -> !typeRegistry.getDirectiveDefinition(def.getName()).isPresent())
        .forEachOrdered(typeRegistry::add);

    // Add scalar type for _FieldSet, since the directives depend on it.
    if (!typeRegistry.getType(_FieldSet.typeName).isPresent()) {
      typeRegistry.add(_FieldSet.definition);
    }

    // Also add the implementation for _FieldSet.
    if (!runtimeWiring.getScalars().containsKey(_FieldSet.typeName)) {
      return runtimeWiring.transform((wiring) -> wiring.scalar(_FieldSet.type));
    } else {
      return runtimeWiring;
    }
  }
}
