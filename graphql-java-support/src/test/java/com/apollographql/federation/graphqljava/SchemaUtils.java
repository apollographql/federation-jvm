package com.apollographql.federation.graphqljava;

import graphql.ExecutionResult;
import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import org.junit.jupiter.api.Assertions;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static graphql.ExecutionInput.newExecutionInput;
import static graphql.GraphQL.newGraphQL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SchemaUtils {
    static public final Set<String> standardDirectives =
            new HashSet<>(Arrays.asList("deprecated", "include", "skip", "specifiedBy"));

    private SchemaUtils() {
    }

    static ExecutionResult execute(GraphQLSchema schema, String query) {
        return newGraphQL(schema).build().execute(newExecutionInput().query(query).build());
    }

    static void assertSDL(GraphQLSchema schema, String expectedSchemaSDL, String expectedServiceSDL) {
        if (expectedSchemaSDL != null) {
            Assertions.assertEquals(
                    expectedSchemaSDL.trim(),
                    new FederationSdlPrinter(FederationSdlPrinter.Options.defaultOptions()
                            .includeScalarTypes(true)
                            .includeDirectiveDefinitions(def -> !standardDirectives.contains(def.getName()))
                    ).print(schema).trim()
            );
        }

        final GraphQLFieldDefinition serviceField = schema.getQueryType().getFieldDefinition("_service");
        assertNotNull(serviceField, "_service field present");
        final GraphQLType serviceType = schema.getType("_Service");
        assertNotNull(serviceType, "_Service type present");
        assertTrue(serviceType instanceof GraphQLObjectType, "_Service type is object type");
        assertEquals(serviceType, serviceField.getType(), "_service returns _Service");
        final GraphQLFieldDefinition sdlField = ((GraphQLObjectType) serviceType).getFieldDefinition("sdl");
        assertNotNull(sdlField, "sdl field present");
        assertTrue(GraphQLNonNull.nonNull(Scalars.GraphQLString).isEqualTo(sdlField.getType()), "sdl returns String!");

        final ExecutionResult inspect = execute(schema, "{_service{sdl}}");
        assertEquals(0, inspect.getErrors().size(), "No errors");
        final Map<String, Object> data = inspect.getData();
        assertNotNull(data);
        @SuppressWarnings("unchecked") final Map<String, Object> _service = (Map<String, Object>) data.get("_service");
        assertNotNull(_service);
        final String sdl = (String) _service.get("sdl");
        assertEquals(expectedServiceSDL.trim(), sdl.trim());
    }
}
