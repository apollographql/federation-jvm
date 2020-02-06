package com.apollographql.federation.graphqljava;

import graphql.ExecutionResult;
import graphql.schema.GraphQLSchema;
import graphql.schema.idl.SchemaPrinter;

import java.util.Map;

import static graphql.ExecutionInput.newExecutionInput;
import static graphql.GraphQL.newGraphQL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

final class SchemaUtils {

    private final static String directivesExclude = TestUtils.readResource("schemas/directives.graphql");

    private SchemaUtils() {
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
        @SuppressWarnings("unchecked") final Map<String, Object> _service = (Map<String, Object>) data.get("_service");
        assertNotNull(_service);
        final String sdl = (String) _service.get("sdl");
        assertEquals(expected.replaceAll("\n\n", "").trim(), sdl.replace(directivesExclude, "").replaceAll("\n\n", "").trim());
    }

    static String removeDirectives(String sdl) {
        return sdl.replace(directivesExclude, "");
    }
}
