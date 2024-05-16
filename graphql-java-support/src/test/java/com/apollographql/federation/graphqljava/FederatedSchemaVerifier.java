package com.apollographql.federation.graphqljava;

import static graphql.ExecutionInput.newExecutionInput;
import static graphql.GraphQL.newGraphQL;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import graphql.ExecutionResult;
import graphql.Scalars;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.idl.DirectiveInfo;
import graphql.schema.idl.SchemaPrinter;
import java.util.Map;
import org.junit.jupiter.api.Assertions;

final class FederatedSchemaVerifier {

  private FederatedSchemaVerifier() {}

  static ExecutionResult execute(GraphQLSchema schema, String query) {
    return newGraphQL(schema).build().execute(newExecutionInput().query(query).build());
  }

  /**
   * Verifies passed in schema generates expected SDL.
   *
   * @param schema test schema
   * @param expectedSchemaSDL expected SDL
   * @param isFederationV2 boolean flag indicating whether we are testing Federation v1 or v2
   *     specification.
   */
  public static void verifySchemaSDL(
      GraphQLSchema schema, String expectedSchemaSDL, boolean isFederationV2) {
    Assertions.assertEquals(
        expectedSchemaSDL.trim(),
        new SchemaPrinter(
                SchemaPrinter.Options.defaultOptions()
                    .includeSchemaDefinition(isFederationV2)
                    .includeScalarTypes(true)
                    .includeDirectives(
                        directive -> !DirectiveInfo.isGraphqlSpecifiedDirective(directive)))
            .print(schema)
            .trim(),
        "Generated schema SDL should match expected one");
  }

  /**
   * Verifies that passed in schema: - contains `_Service { sdl: String! }` type - contains
   * `_service: _Service` query
   *
   * @param schema schema to be verified
   */
  public static void verifySchemaContainsServiceFederationType(GraphQLSchema schema) {
    final GraphQLFieldDefinition serviceField =
        schema.getQueryType().getFieldDefinition("_service");
    assertNotNull(serviceField, "_service field present");
    final GraphQLType serviceType = schema.getType("_Service");
    assertNotNull(serviceType, "_Service type present");
    assertTrue(serviceType instanceof GraphQLObjectType, "_Service type is object type");
    assertTrue(
        serviceField.getType() instanceof GraphQLNonNull, "_service returns non-nullable object");
    final GraphQLNonNull nonNullableServiceType = (GraphQLNonNull) serviceField.getType();
    assertEquals(
        serviceType,
        nonNullableServiceType.getWrappedType(),
        "_service returns non-nullable _Service");
    final GraphQLFieldDefinition sdlField =
        ((GraphQLObjectType) serviceType).getFieldDefinition("sdl");
    assertNotNull(sdlField, "sdl field present");
    assertTrue(
        GraphQLNonNull.nonNull(Scalars.GraphQLString).isEqualTo(sdlField.getType()),
        "sdl returns String!");
  }

  /**
   * Verifies `_service { sdl }` query returns expected SDL
   *
   * @param schema test schema
   * @param expectedServiceSDL expected SDL
   */
  public static void verifyServiceSDL(GraphQLSchema schema, String expectedServiceSDL) {
    final ExecutionResult inspect = execute(schema, "{_service{sdl}}");
    assertEquals(0, inspect.getErrors().size(), "No errors");
    final Map<String, Object> data = inspect.getData();
    assertNotNull(data);
    @SuppressWarnings("unchecked")
    final Map<String, Object> _service = (Map<String, Object>) data.get("_service");
    assertNotNull(_service);
    final String sdl = (String) _service.get("sdl");
    assertEquals(expectedServiceSDL.trim(), sdl.trim());
  }
}
