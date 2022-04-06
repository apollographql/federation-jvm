package com.apollographql.federation.graphqljava;

import graphql.language.DirectiveDefinition;
import graphql.language.FieldDefinition;
import graphql.language.ObjectTypeDefinition;
import graphql.language.StringValue;
import graphql.language.TypeDefinition;
import graphql.language.TypeName;
import graphql.language.Value;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import java.io.File;
import java.io.Reader;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.NotNull;

public final class Federation {
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

    boolean isFederation2 = isFederation2(typeRegistry);
    RuntimeWiring newRuntimeWiring =
        ensureFederationDirectiveDefinitionsExist(typeRegistry, runtimeWiring, isFederation2);
    final GraphQLSchema original =
        new SchemaGenerator()
            .makeExecutableSchema(generatorOptions, typeRegistry, newRuntimeWiring);
    return transform(original, queryTypeShouldBeEmpty).setFederation2(isFederation2);
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

  private static RuntimeWiring ensureFederationDirectiveDefinitionsExist(
      TypeDefinitionRegistry typeRegistry, RuntimeWiring runtimeWiring, boolean isFederation2) {
    Set<DirectiveDefinition> directivesToAdd;
    if (isFederation2) {
      directivesToAdd = FederationDirectives.federation2DirectiveDefinitions;
    } else {
      directivesToAdd = FederationDirectives.federation1DirectiveDefinitions;
    }

    // Add Federation directives if they don't exist.
    directivesToAdd.stream()
        .filter(def -> !typeRegistry.getDirectiveDefinition(def.getName()).isPresent())
        .forEachOrdered(typeRegistry::add);

    // Add scalar type for _FieldSet, since the directives depend on it.
    if (!typeRegistry.getType(_FieldSet.typeName).isPresent()) {
      typeRegistry.add(_FieldSet.definition);
    }

    // Also add the implementation for _FieldSet.
    RuntimeWiring newRuntimeWiring = runtimeWiring;
    if (!runtimeWiring.getScalars().containsKey(_FieldSet.typeName)) {
      newRuntimeWiring = copyRuntimeWiring(newRuntimeWiring, Collections.singleton(_FieldSet.type));
    }

    // Add scalar type for link__Import, since the directives depend on it.
    if (isFederation2) {
      if (!typeRegistry.getType(link__Import.typeName).isPresent()) {
        typeRegistry.add(link__Import.definition);
      }
      if (!runtimeWiring.getScalars().containsKey(link__Import.typeName)) {
        newRuntimeWiring =
            copyRuntimeWiring(newRuntimeWiring, Collections.singleton(link__Import.type));
      }
    }

    return newRuntimeWiring;
  }

  private static RuntimeWiring copyRuntimeWiring(
      RuntimeWiring runtimeWiring, Set<GraphQLScalarType> additionalScalars) {
    // Annoyingly graphql-java doesn't have a copy constructor for RuntimeWiring.Builder.
    RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring();

    runtimeWiring.getDataFetchers().entrySet().stream()
        .map(
            entry -> {
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
    additionalScalars.forEach(builder::scalar);
    if (runtimeWiring.getFieldVisibility() != null) {
      builder.fieldVisibility(runtimeWiring.getFieldVisibility());
    }
    runtimeWiring.getRegisteredDirectiveWiring().forEach(builder::directive);
    runtimeWiring.getDirectiveWiring().forEach(builder::directiveWiring);
    builder.comparatorRegistry(runtimeWiring.getComparatorRegistry());
    runtimeWiring.getSchemaGeneratorPostProcessings().forEach(builder::transformer);

    RuntimeWiring runtimeWiringCopy = builder.build();
    runtimeWiring
        .getTypeResolvers()
        .forEach((key, value) -> runtimeWiringCopy.getTypeResolvers().putIfAbsent(key, value));
    return runtimeWiringCopy;
  }

  public static boolean isFederation2(TypeDefinitionRegistry typeDefinitionRegistry) {
    return typeDefinitionRegistry.getSchemaExtensionDefinitions().stream()
        .anyMatch(
            schemaExtensionDefinition ->
                schemaExtensionDefinition.getDirectives().stream()
                    .anyMatch(
                        directive ->
                            directive.getName().equals("link")
                                && directive.getArguments().stream()
                                    .anyMatch(
                                        argument -> {
                                          Value value = argument.getValue();
                                          return argument.getName().equals("url")
                                              && value instanceof StringValue
                                              && ((StringValue) value)
                                                  .getValue()
                                                  .equals(
                                                      "https://specs.apollo.dev/federation/v2.0");
                                        })));
  }
}
