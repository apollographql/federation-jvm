package com.apollographql.federation.graphqljava;

import com.apollographql.federation.graphqljava.exceptions.UnsupportedLinkImportException;
import graphql.language.Argument;
import graphql.language.ArrayValue;
import graphql.language.AstTransformer;
import graphql.language.Directive;
import graphql.language.FieldDefinition;
import graphql.language.ObjectField;
import graphql.language.ObjectTypeDefinition;
import graphql.language.ObjectValue;
import graphql.language.SDLNamedDefinition;
import graphql.language.ScalarTypeDefinition;
import graphql.language.SchemaDefinition;
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
import java.io.File;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

    @Nullable Map<String, String> fed2Imports = fed2DirectiveImports(typeRegistry);
    RuntimeWiring newRuntimeWiring;

    if (fed2Imports != null) {
      newRuntimeWiring =
          ensureFederationV2DirectiveDefinitionsExist(typeRegistry, runtimeWiring, fed2Imports);
    } else {
      newRuntimeWiring = ensureFederationDirectiveDefinitionsExist(typeRegistry, runtimeWiring);
    }
    final GraphQLSchema original =
        new SchemaGenerator()
            .makeExecutableSchema(generatorOptions, typeRegistry, newRuntimeWiring);
    return transform(original, queryTypeShouldBeEmpty).setFederation2(fed2Imports != null);
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

  private static Stream<SDLNamedDefinition> renameDefinitions(
      List<SDLNamedDefinition> sdlNamedDefinition, Map<String, String> fed2Imports) {
    return sdlNamedDefinition.stream()
        .map(
            definition ->
                (SDLNamedDefinition)
                    new AstTransformer()
                        .transform(definition, new LinkImportsRenamingVisitor(fed2Imports)));
  }

  private static RuntimeWiring ensureFederationV2DirectiveDefinitionsExist(
      TypeDefinitionRegistry typeRegistry,
      RuntimeWiring runtimeWiring,
      @Nullable Map<String, String> fed2Imports) {

    HashSet<GraphQLScalarType> scalarTypesToAdd = new HashSet<>();
    Stream<SDLNamedDefinition> renamedDefinitions =
        renameDefinitions(FederationDirectives.federation2Definitions, fed2Imports);

    renamedDefinitions.forEach(
        def -> {
          if (!typeRegistry.getDirectiveDefinition(def.getName()).isPresent()) {
            typeRegistry.add(def);
          }
          if (def instanceof ScalarTypeDefinition
              && !runtimeWiring.getScalars().containsKey(def.getName())) {
            scalarTypesToAdd.add(
                GraphQLScalarType.newScalar()
                    .name(def.getName())
                    .description(null)
                    .coercing(_Any.defaultCoercing)
                    .build());
          }
        });
    return addScalarsToRuntimeWiring(runtimeWiring, scalarTypesToAdd);
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
    final RuntimeWiring newRuntimeWiring;
    if (!runtimeWiring.getScalars().containsKey(_FieldSet.typeName)) {
      newRuntimeWiring =
          addScalarsToRuntimeWiring(runtimeWiring, Collections.singleton(_FieldSet.type));
    } else {
      newRuntimeWiring = runtimeWiring;
    }
    return newRuntimeWiring;
  }

  private static RuntimeWiring addScalarsToRuntimeWiring(
      RuntimeWiring runtimeWiring, Set<GraphQLScalarType> additionalScalars) {
    RuntimeWiring.Builder builder = RuntimeWiring.newRuntimeWiring(runtimeWiring);
    additionalScalars.forEach(builder::scalar);
    return builder.build();
  }

  /**
   * Looks for a @link directive and parses imports information from it.
   *
   * @return - null if this typeDefinitionRegistry is not Fed2 - the list of imports and potential
   *     renames else
   */
  private static @Nullable Map<String, String> fed2DirectiveImports(
      TypeDefinitionRegistry typeDefinitionRegistry) {
    List<Directive> federationLinkDirectives = typeDefinitionRegistry.schemaDefinition()
      .map(Federation::getFederationLinkDirective)
      .map(Collections::singletonList)
      .orElseGet(() -> typeDefinitionRegistry.getSchemaExtensionDefinitions()
        .stream()
        .map(Federation::getFederationLinkDirective)
        .filter(Objects::nonNull)
        .collect(Collectors.toList())
      );

    if (federationLinkDirectives.isEmpty()) {
      return null;
    } else {
      Map<String, String> imports = new HashMap<>();
      federationLinkDirectives.forEach(
        directive -> imports.putAll(parseLinkImports(directive))
      );

      imports.put("@link", "@link");
      return imports;
    }
  }

  private static @Nullable Directive getFederationLinkDirective(SchemaDefinition schemaDefinition) {
    return schemaDefinition.getDirectives("link")
      .stream()
      .filter(directive -> {
        Argument urlArgument = directive.getArgument("url");
        if (urlArgument != null && urlArgument.getValue() instanceof StringValue) {
          StringValue value = (StringValue) urlArgument.getValue();
          return "https://specs.apollo.dev/federation/v2.0".equals(value.getValue());
        } else {
          return false;
        }
      })
      .findAny()
      .orElse(null);
  }

  private static Map<String, String> parseLinkImports(Directive linkDirective) {
    final Map<String, String> imports = new HashMap<>();

    final Argument importArgument = linkDirective.getArgument("import");
    if (importArgument != null && importArgument.getValue() instanceof ArrayValue) {
      final ArrayValue linkImports = (ArrayValue) importArgument.getValue();
      for (Value importedDefinition : linkImports.getValues()) {
        if (importedDefinition instanceof StringValue) {
          // no rename
          final String name = ((StringValue) importedDefinition).getValue();
          imports.put(name, name);
        } else if (importedDefinition instanceof ObjectValue) {
          // renamed import
          final ObjectValue importedObjectValue = (ObjectValue) importedDefinition;

          final Optional<ObjectField> nameField =
            importedObjectValue.getObjectFields().stream()
              .filter(field -> field.getName().equals("name"))
              .findFirst();
          final Optional<ObjectField> renameAsField =
            importedObjectValue.getObjectFields().stream()
              .filter(field -> field.getName().equals("as"))
              .findFirst();

          if (!nameField.isPresent() || !(nameField.get().getValue() instanceof StringValue)) {
            throw new UnsupportedLinkImportException(importedObjectValue);
          }
          final String name = ((StringValue) nameField.get().getValue()).getValue();

          if (!renameAsField.isPresent()) {
            imports.put(name, name);
          } else {
            final Value renamedAsValue = renameAsField.get().getValue();
            if (!(renamedAsValue instanceof StringValue)) {
              throw new UnsupportedLinkImportException(importedObjectValue);
            }
            imports.put(name, ((StringValue) renamedAsValue).getValue());
          }
        } else {
          throw new UnsupportedLinkImportException(importedDefinition);
        }
      }
    }
    return imports;
  }
}
