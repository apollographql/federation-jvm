package com.apollographql.federation.graphqljava;

import graphql.ExecutionResult;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.*;

import java.util.Map;

import static graphql.ExecutionInput.newExecutionInput;
import static graphql.nextgen.GraphQL.newGraphQL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class SchemaUtils {
    private static final RuntimeWiring noop = RuntimeWiring.newRuntimeWiring().build();

    static GraphQLSchema buildSchema(String sdl) {
        TypeDefinitionRegistry typeRegistry = new SchemaParser().parse(sdl);
        typeRegistry.addAll(FederationDirectives.allDefinitions);
        return new SchemaGenerator().makeExecutableSchema(typeRegistry, noop);
    }

    static String printSchema(GraphQLSchema schema) {
        return new SchemaPrinter().print(schema);
    }

    static ExecutionResult execute(GraphQLSchema schema, String query) {
        return newGraphQL(schema).build().execute(newExecutionInput().query(query).build());
    }

    static void assertSDL(GraphQLSchema schema, String expected) {
        final ExecutionResult inspect = execute(schema, "{_service{sdl}}");
        assertEquals(0, inspect.getErrors().size(), "No errors");
        final Map<String, Object> data = inspect.getData();
        assertNotNull(data);
        final Map<String, Object> _service = (Map<String, Object>) data.get("_service");
        assertNotNull(_service);
        final String sdl = (String) _service.get("sdl");
        assertEquals(expected.trim(), sdl.trim());
    }

    private SchemaUtils() {
    }
}
