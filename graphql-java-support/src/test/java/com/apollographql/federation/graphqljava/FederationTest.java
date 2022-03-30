package com.apollographql.federation.graphqljava;

import static com.apollographql.federation.graphqljava.SchemaUtils.standardDirectives;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import graphql.ExecutionResult;
import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import graphql.schema.idl.errors.SchemaProblem;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class FederationTest {
  private final String emptySDL = TestUtils.readResource("schemas/empty.graphql");
  private final String emptyFederatedSDL = TestUtils.readResource("schemas/emptyFederated.graphql");
  private final String emptySchemaFederatedSDL =
      TestUtils.readResource("schemas/emptySchemaFederated.graphql");
  private final String emptyWithExtendQuerySDL =
      TestUtils.readResource("schemas/emptyWithExtendQuery.graphql");
  private final String emptyWithExtendQueryFederatedSDL =
      TestUtils.readResource("schemas/emptyWithExtendQueryFederated.graphql");
  private final String emptyWithExtendQueryServiceSDL =
      TestUtils.readResource("schemas/emptyWithExtendQueryService.graphql");
  private final String interfacesSDL = TestUtils.readResource("schemas/interfaces.graphql");
  private final String isolatedSDL = TestUtils.readResource("schemas/isolated.graphql");
  private final String productSDL = TestUtils.readResource("schemas/product.graphql");
  private final String printerEscapingSDL =
      TestUtils.readResource("schemas/printerEscaping.graphql");
  private final String printerEscapingExpectedSDL =
      TestUtils.readResource("schemas/printerEscapingExpected.graphql");
  private final String printerFilterSDL = TestUtils.readResource("schemas/printerFilter.graphql");
  private final String printerFilterExpectedSDL =
      TestUtils.readResource("schemas/printerFilterExpected.graphql");
  private final String fed2SDL = TestUtils.readResource("schemas/fed2.graphql");
  private final String fed2FederatedSDL = TestUtils.readResource("schemas/fed2Federated.graphql");
  private final String fed2ServiceSDL = TestUtils.readResource("schemas/fed2Service.graphql");
  private final String noNewEntitySDL = TestUtils.readResource("schemas/noNewEntity.graphql");
  private final String noNewEntityFederatedSDL = TestUtils.readResource("schemas/noNewEntityFederated.graphql");
  private final String noNewEntityServiceSDL = TestUtils.readResource("schemas/noNewEntityService.graphql");

  @Test
  void testEmptySDL() {
    final GraphQLSchema federatedSchema = Federation.transform(emptySDL).build();
    SchemaUtils.assertSDL(federatedSchema, emptyFederatedSDL, emptySDL);
  }

  @Test
  void testEmptyWithExtendQuerySDL() {
    final GraphQLSchema federatedSchema = Federation.transform(emptyWithExtendQuerySDL).build();
    SchemaUtils.assertSDL(
        federatedSchema, emptyWithExtendQueryFederatedSDL, emptyWithExtendQueryServiceSDL);
  }

  @Test
  void testEmptySchema() {
    final GraphQLSchema federatedSchema =
        Federation.transform(
                GraphQLSchema.newSchema()
                    .query(
                        GraphQLObjectType.newObject()
                            .name("Query")
                            .field(
                                GraphQLFieldDefinition.newFieldDefinition()
                                    .name("dummy")
                                    .type(Scalars.GraphQLString)
                                    .build())
                            .build())
                    .build(),
                true)
            .build();
    SchemaUtils.assertSDL(federatedSchema, emptySchemaFederatedSDL, emptySDL);
  }

  @Test
  void testRequirements() {
    assertThrows(SchemaProblem.class, () -> Federation.transform(productSDL).build());
    assertThrows(
        SchemaProblem.class,
        () -> Federation.transform(productSDL).resolveEntityType(env -> null).build());
    assertThrows(
        SchemaProblem.class,
        () -> Federation.transform(productSDL).fetchEntities(env -> null).build());
  }

  @Test
  void testSimpleService() {
    final GraphQLSchema federated =
        Federation.transform(productSDL)
            .fetchEntities(
                env ->
                    env.<List<Map<String, Object>>>getArgument(_Entity.argumentName).stream()
                        .map(
                            map -> {
                              if ("Product".equals(map.get("__typename"))) {
                                return Product.PLANCK;
                              }
                              return null;
                            })
                        .collect(Collectors.toList()))
            .resolveEntityType(env -> env.getSchema().getObjectType("Product"))
            .build();

    SchemaUtils.assertSDL(federated, null, productSDL);

    final ExecutionResult result =
        SchemaUtils.execute(
            federated,
            "{\n"
                + "  _entities(representations: [{__typename:\"Product\"}]) {\n"
                + "    ... on Product { price }\n"
                + "  }"
                + "}");
    assertEquals(0, result.getErrors().size(), "No errors");

    final Map<String, Object> data = result.getData();
    @SuppressWarnings("unchecked")
    final List<Map<String, Object>> _entities = (List<Map<String, Object>>) data.get("_entities");

    assertEquals(1, _entities.size());
    assertEquals(180, _entities.get(0).get("price"));
  }

  // From https://github.com/apollographql/federation-jvm/issues/7
  @Test
  void testSchemaTransformationIsolated() {
    Federation.transform(isolatedSDL)
        .resolveEntityType(env -> null)
        .fetchEntities(environment -> null)
        .build();
    Federation.transform(isolatedSDL)
        .resolveEntityType(env -> null)
        .fetchEntities(environment -> null)
        .build();
  }

  @Test
  void testInterfacesAreCovered() {
    final RuntimeWiring wiring =
        RuntimeWiring.newRuntimeWiring()
            .type(TypeRuntimeWiring.newTypeWiring("Product").typeResolver(env -> null).build())
            .build();

    final GraphQLSchema transformed =
        Federation.transform(interfacesSDL, wiring)
            .resolveEntityType(env -> null)
            .fetchEntities(environment -> null)
            .build();

    final GraphQLUnionType entityType = (GraphQLUnionType) transformed.getType(_Entity.typeName);

    final Iterable<String> unionTypes =
        entityType.getTypes().stream()
            .map(GraphQLNamedType::getName)
            .sorted()
            .collect(Collectors.toList());

    assertIterableEquals(Arrays.asList("Book", "Movie", "Page"), unionTypes);
  }

  @Test
  void testPrinterEscaping() {
    TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(printerEscapingSDL);
    GraphQLSchema graphQLSchema =
        new SchemaGenerator()
            .makeExecutableSchema(typeDefinitionRegistry, RuntimeWiring.newRuntimeWiring().build());
    Assertions.assertEquals(
        printerEscapingExpectedSDL.trim(),
        new FederationSdlPrinter(
                FederationSdlPrinter.Options.defaultOptions()
                    .includeDirectiveDefinitions(
                        def -> !standardDirectives.contains(def.getName())))
            .print(graphQLSchema)
            .trim());
  }

  @Test
  void testPrinterFilter() {
    TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(printerFilterSDL);
    RuntimeWiring runtimeWiring =
        RuntimeWiring.newRuntimeWiring()
            .type("Interface1", typeWiring -> typeWiring.typeResolver(env -> null))
            .type("Interface2", typeWiring -> typeWiring.typeResolver(env -> null))
            .scalar(
                GraphQLScalarType.newScalar()
                    .name("Scalar1")
                    .coercing(Scalars.GraphQLString.getCoercing())
                    .build())
            .scalar(
                GraphQLScalarType.newScalar()
                    .name("Scalar2")
                    .coercing(Scalars.GraphQLString.getCoercing())
                    .build())
            .build();
    GraphQLSchema graphQLSchema =
        new SchemaGenerator().makeExecutableSchema(typeDefinitionRegistry, runtimeWiring);
    Assertions.assertEquals(
        printerFilterExpectedSDL.trim(),
        new FederationSdlPrinter(
                FederationSdlPrinter.Options.defaultOptions()
                    .includeScalarTypes(true)
                    .includeDirectiveDefinitions(
                        def ->
                            !def.getName().endsWith("1")
                                && !standardDirectives.contains(def.getName()))
                    .includeTypeDefinitions(def -> !def.getName().endsWith("1")))
            .print(graphQLSchema)
            .trim());
  }

  @Test
  void testFed2() {
    final GraphQLSchema federatedSchema = Federation.transform(fed2SDL).build();
    SchemaUtils.assertSDL(federatedSchema, fed2FederatedSDL, fed2ServiceSDL);
  }

  @Test
  void testNoNewEntities() {
    final GraphQLSchema federatedSchema = Federation.transform(noNewEntitySDL).build();
    SchemaUtils.assertSDL(federatedSchema, noNewEntityFederatedSDL, noNewEntityServiceSDL);
  }

}
