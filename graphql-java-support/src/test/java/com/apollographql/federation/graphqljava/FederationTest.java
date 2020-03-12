package com.apollographql.federation.graphqljava;

import graphql.ExecutionResult;
import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLUnionType;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.SchemaGenerator;
import graphql.schema.idl.SchemaParser;
import graphql.schema.idl.TypeDefinitionRegistry;
import graphql.schema.idl.TypeRuntimeWiring;
import graphql.schema.idl.errors.SchemaProblem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FederationTest {
    private final String emptySDL = TestUtils.readResource("schemas/empty.graphql");
    private final String interfacesSDL = TestUtils.readResource("schemas/interfaces.graphql");
    private final String isolatedSDL = TestUtils.readResource("schemas/isolated.graphql");
    private final String productSDL = TestUtils.readResource("schemas/product.graphql");
    private final String printerEmptySDL = TestUtils.readResource("schemas/printerEmpty.graphql");
    private final String printerFilterSDL = TestUtils.readResource("schemas/printerFilter.graphql");
    private final String printerFilterExpectedSDL = TestUtils.readResource("schemas/printerFilterExpected.graphql");

    @Test
    void testEmpty() {
        final GraphQLSchema federated = Federation.transform(emptySDL)
                .build();
        Assertions.assertEquals("directive @extends on OBJECT\n" +
                "\n" +
                "directive @external on FIELD_DEFINITION\n" +
                "\n" +
                "directive @key(fields: _FieldSet!) on OBJECT | INTERFACE\n" +
                "\n" +
                "directive @provides(fields: _FieldSet!) on FIELD_DEFINITION\n" +
                "\n" +
                "directive @requires(fields: _FieldSet!) on FIELD_DEFINITION\n" +
                "\n" +
                "type Query {\n" +
                "  _service: _Service\n" +
                "}\n" +
                "\n" +
                "type _Service {\n" +
                "  sdl: String!\n" +
                "}\n" +
                "\n" +
                "scalar _FieldSet\n", SchemaUtils.printSchema(federated));

        final GraphQLType _Service = federated.getType("_Service");
        assertNotNull(_Service, "_Service type present");
        final GraphQLFieldDefinition _service = federated.getQueryType().getFieldDefinition("_service");
        assertNotNull(_service, "_service field present");
        assertEquals(_Service, _service.getType(), "_service returns _Service");

        SchemaUtils.assertSDL(federated, emptySDL);
    }

    @Test
    void testRequirements() {
        assertThrows(SchemaProblem.class, () ->
                Federation.transform(productSDL).build());
        assertThrows(SchemaProblem.class, () ->
                Federation.transform(productSDL).resolveEntityType(env -> null).build());
        assertThrows(SchemaProblem.class, () ->
                Federation.transform(productSDL).fetchEntities(env -> null).build());
    }

    @Test
    void testSimpleService() {
        final GraphQLSchema federated = Federation.transform(productSDL)
                .fetchEntities(env ->
                        env.<List<Map<String, Object>>>getArgument(_Entity.argumentName)
                                .stream()
                                .map(map -> {
                                    if ("Product".equals(map.get("__typename"))) {
                                        return Product.PLANCK;
                                    }
                                    return null;
                                })
                                .collect(Collectors.toList()))
                .resolveEntityType(env -> env.getSchema().getObjectType("Product"))
                .build();

        SchemaUtils.assertSDL(federated, productSDL);

        final ExecutionResult result = SchemaUtils.execute(federated, "{\n" +
                "  _entities(representations: [{__typename:\"Product\"}]) {\n" +
                "    ... on Product { price }\n" +
                "  }" +
                "}");
        assertEquals(0, result.getErrors().size(), "No errors");

        final Map<String, Object> data = result.getData();
        @SuppressWarnings("unchecked") final List<Map<String, Object>> _entities = (List<Map<String, Object>>) data.get("_entities");

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
        final RuntimeWiring wiring = RuntimeWiring.newRuntimeWiring()
                .type(TypeRuntimeWiring.newTypeWiring("Product")
                        .typeResolver(env -> null)
                        .build())
                .build();

        final GraphQLSchema transformed = Federation.transform(interfacesSDL, wiring)
                .resolveEntityType(env -> null)
                .fetchEntities(environment -> null)
                .build();

        final GraphQLUnionType entityType = (GraphQLUnionType) transformed.getType(_Entity.typeName);

        final Iterable<String> unionTypes = entityType
                .getTypes()
                .stream()
                .map(GraphQLNamedType::getName)
                .sorted()
                .collect(Collectors.toList());

        assertIterableEquals(Arrays.asList("Book", "Movie", "Page"), unionTypes);
    }

    @Test
    void testPrinterEmpty() {
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(printerEmptySDL);
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Interface1", typeWiring -> typeWiring
                        .typeResolver(env -> null)
                )
                .type("Interface2", typeWiring -> typeWiring
                        .typeResolver(env -> null)
                )
                .build();
        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(
                typeDefinitionRegistry,
                runtimeWiring
        );
        Assertions.assertEquals(printerEmptySDL.trim(), new FederationSdlPrinter().print(graphQLSchema).trim());
    }

    @Test
    void testPrinterFilter() {
        TypeDefinitionRegistry typeDefinitionRegistry = new SchemaParser().parse(printerFilterSDL);
        RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring()
                .type("Interface1", typeWiring -> typeWiring
                        .typeResolver(env -> null)
                )
                .type("Interface2", typeWiring -> typeWiring
                        .typeResolver(env -> null)
                )
                .scalar(GraphQLScalarType.newScalar()
                        .name("Scalar1")
                        .coercing(Scalars.GraphQLString.getCoercing())
                        .build()
                )
                .scalar(GraphQLScalarType.newScalar()
                        .name("Scalar2")
                        .coercing(Scalars.GraphQLString.getCoercing())
                        .build()
                )
                .build();
        GraphQLSchema graphQLSchema = new SchemaGenerator().makeExecutableSchema(
                typeDefinitionRegistry,
                runtimeWiring
        );
        Assertions.assertEquals(
                printerFilterExpectedSDL.trim(),
                new FederationSdlPrinter(FederationSdlPrinter.Options.defaultOptions()
                        .includeScalarTypes(true)
                        .includeDirectiveDefinitions(def -> !def.getName().endsWith("1"))
                        .includeTypeDefinitions(def -> !def.getName().endsWith("1"))
                ).print(graphQLSchema).trim()
        );
    }
}
