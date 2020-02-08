package io.gqljf.federation;

import graphql.ExecutionResult;
import graphql.schema.GraphQLSchema;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static graphql.ExecutionInput.newExecutionInput;
import static graphql.GraphQL.newGraphQL;

abstract class AbstractTest {

    protected ExecutionResult execute(GraphQLSchema schema, String query) {
        return newGraphQL(schema).build().execute(newExecutionInput().query(query).build());
    }

    protected ExecutionResult execute(GraphQLSchema schema, String query, Map<String, Object> variables) {
        return newGraphQL(schema).build().execute(newExecutionInput()
                .query(query)
                .variables(variables)
                .build());
    }

    protected InputStream getResourceAsStream(String fileName) throws Exception {
        Path resourcePath = Paths.get("src", "test", "resources", fileName);
        return new FileInputStream(resourcePath.toFile());
    }

    protected String getResourceAsString(String fileName) throws Exception {
        Path resourcePath = Paths.get("src", "test", "resources", fileName);
        return Files.readString(resourcePath);
    }
}
