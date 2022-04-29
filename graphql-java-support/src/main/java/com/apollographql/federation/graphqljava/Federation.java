package com.apollographql.federation.graphqljava;

import static graphql.util.TreeTransformerUtil.changeNode;

import graphql.language.*;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import graphql.util.TraversalControl;
import graphql.util.TraverserContext;
import java.io.File;
import java.io.Reader;
import java.util.*;
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
          ensureFederation2DirectiveDefinitionsExist(typeRegistry, runtimeWiring, fed2Imports);
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
            definition -> {
              SDLNamedDefinition newDefinition =
                  (SDLNamedDefinition)
                      new AstTransformer().transform(definition, new RenamingVisitor(fed2Imports));
              return newDefinition;
            });
  }

  private static String newName(String name, Map<String, String> fed2Imports, boolean isDirective) {
    String key;
    if (isDirective) {
      key = "@" + name;
    } else {
      key = name;
    }

    if (key.equals("String")
        || key.equals("Boolean")
        || key.equals("Int")
        || key.equals("Float")
        || key.equals("ID")) {
      // Do not rename builtin types
      return name;
    }

    if (fed2Imports.containsKey(key)) {
      String newName = fed2Imports.get(key);
      if (isDirective) {
        return newName.substring(1);
      } else {
        return newName;
      }
    } else {
      return "federation__" + name;
    }
  }

  static class RenamingVisitor extends NodeVisitorStub {
    private Map<String, String> fed2Imports;

    public RenamingVisitor(Map<String, String> fed2Imports) {
      this.fed2Imports = fed2Imports;
    }

    @Override
    protected TraversalControl visitNode(Node node, TraverserContext<Node> context) {
      if (node instanceof NamedNode) {
        Node newNode = null;
        if (node instanceof TypeName) {
          String newName = newName(((NamedNode<?>) node).getName(), fed2Imports, false);
          newNode = ((TypeName) node).transform(builder -> builder.name(newName));
        } else if (node instanceof ScalarTypeDefinition) {
          String newName = newName(((NamedNode<?>) node).getName(), fed2Imports, false);
          newNode = ((ScalarTypeDefinition) node).transform(builder -> builder.name(newName));
        } else if (node instanceof DirectiveDefinition) {
          String newName = newName(((NamedNode<?>) node).getName(), fed2Imports, true);
          newNode = ((DirectiveDefinition) node).transform(builder -> builder.name(newName));
        }
        if (newNode != null) {
          return changeNode(context, newNode);
        }
      }
      return super.visitNode(node, context);
    }
  }

  private static RuntimeWiring ensureFederation2DirectiveDefinitionsExist(
      TypeDefinitionRegistry typeRegistry,
      RuntimeWiring runtimeWiring,
      @Nullable Map<String, String> fed2Imports) {

    RuntimeWiring newRuntimeWiring = runtimeWiring;
    HashSet<GraphQLScalarType> scalarTypesToAdd = new HashSet<GraphQLScalarType>();

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
    return copyRuntimeWiring(newRuntimeWiring, scalarTypesToAdd);
  }

  private static RuntimeWiring ensureFederationDirectiveDefinitionsExist(
      TypeDefinitionRegistry typeRegistry, RuntimeWiring runtimeWiring) {

    Set<DirectiveDefinition> directivesToAdd;
    directivesToAdd = FederationDirectives.federation1DirectiveDefinitions;

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

    // Add hardcoded @link to avoid having federation__link all over the place
    imports.put("@link", "@link");
    return imports;
  }
}
