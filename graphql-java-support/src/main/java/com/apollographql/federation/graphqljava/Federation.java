package com.apollographql.federation.graphqljava;

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
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
   * Looks for an @link extension and gets the import from it
   *
   * @return - null if this typeDefinitionRegistry is not Fed2 - the list of imports and potential
   *     renames else
   */
  private static @Nullable Map<String, String> fed2DirectiveImports(
      TypeDefinitionRegistry typeDefinitionRegistry) {
    List<Directive> linkDirectives =
        typeDefinitionRegistry.getSchemaExtensionDefinitions().stream()
            .flatMap(
                schemaExtensionDefinition ->
                    schemaExtensionDefinition.getDirectives().stream()
                        .filter(directive -> directive.getName().equals("link")))
            .filter(
                directive -> {
                  Optional<Argument> arg =
                      directive.getArguments().stream()
                          .filter(argument -> argument.getName().equals("url"))
                          .findFirst();

                  if (!arg.isPresent()) {
                    return false;
                  }

                  Value value = arg.get().getValue();
                  if (!(value instanceof StringValue)) {
                    return false;
                  }

                  StringValue stringValue = (StringValue) value;
                  return stringValue.getValue().equals("https://specs.apollo.dev/federation/v2.0");
                })
            .collect(Collectors.toList());

    if (linkDirectives.isEmpty()) {
      return null;
    }

    Map<String, String> imports =
        linkDirectives.stream()
            .flatMap(
                directive -> {
                  Optional<Argument> arg =
                      directive.getArguments().stream()
                          .filter(argument -> argument.getName().equals("import"))
                          .findFirst();

                  if (!arg.isPresent()) {
                    return Stream.empty();
                  }

                  Value value = arg.get().getValue();
                  if (!(value instanceof ArrayValue)) {
                    return Stream.empty();
                  }

                  ArrayValue arrayValue = (ArrayValue) value;

                  List<Map.Entry<String, String>> entries = new ArrayList<>();

                  for (Value imp : arrayValue.getValues()) {
                    if (imp instanceof StringValue) {
                      String name = ((StringValue) imp).getValue();
                      entries.add(new AbstractMap.SimpleEntry(name, name));
                    } else if (imp instanceof ObjectValue) {
                      ObjectValue objectValue = (ObjectValue) imp;
                      Optional<ObjectField> nameField =
                          objectValue.getObjectFields().stream()
                              .filter(field -> field.getName().equals("name"))
                              .findFirst();
                      Optional<ObjectField> asField =
                          objectValue.getObjectFields().stream()
                              .filter(field -> field.getName().equals("as"))
                              .findFirst();

                      if (!nameField.isPresent()) {
                        throw new RuntimeException("Unsupported import: " + imp);
                      }

                      Value nameValue = nameField.get().getValue();
                      if (!(nameValue instanceof StringValue)) {
                        throw new RuntimeException("Unsupported import: " + imp);
                      }

                      String as;
                      if (!asField.isPresent()) {
                        as = null;
                      } else {
                        Value asValue = asField.get().getValue();
                        if (!(asValue instanceof StringValue)) {
                          throw new RuntimeException("Unsupported import: " + imp);
                        }
                        as = ((StringValue) asValue).getValue();
                      }

                      entries.add(
                          new AbstractMap.SimpleEntry(((StringValue) nameValue).getValue(), as));
                    } else {
                      throw new RuntimeException("Unsupported import: " + imp.toString());
                    }
                  }

                  return entries.stream();
                })
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey, Map.Entry::getValue, (value1, value2) -> value2));

    imports.put("@link", "@link");
    return imports;
  }
}
