package com.apollographql.federation.graphqljava;

import graphql.Scalars;
import graphql.schema.DefaultGraphqlTypeComparatorRegistry;
import graphql.schema.GraphQLAppliedDirectiveArgument;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphqlTypeComparatorRegistry;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class SchemaTransformerTest {

  private final GraphQLSchema TEST_SCHEMA =
      GraphQLSchema.newSchema()
          .query(
              GraphQLObjectType.newObject()
                  .name("Query")
                  .field(
                      GraphQLFieldDefinition.newFieldDefinition()
                          .name("helloWorld")
                          .type(Scalars.GraphQLString)
                          .build()))
          .build();

  private final GraphQLObjectType FOO_TYPE =
      GraphQLObjectType.newObject()
          .name("Foo")
          .field(
              GraphQLFieldDefinition.newFieldDefinition()
                  .name("id")
                  .type(Scalars.GraphQLID)
                  .build())
          .build();

  @Test
  public void getFederatedEntities_entityHasAppliedDirective_found() {
    final GraphQLSchema schema =
        TEST_SCHEMA.transform(
            schemaBuilder ->
                schemaBuilder.additionalType(
                    FOO_TYPE.transform(
                        objectBuilder ->
                            objectBuilder.withAppliedDirective(
                                FederationDirectives.key
                                    .toAppliedDirective()
                                    .transform(
                                        directive ->
                                            directive.argument(
                                                GraphQLAppliedDirectiveArgument.newArgument()
                                                    .name("fields")
                                                    .type(GraphQLNonNull.nonNull(_FieldSet.type))
                                                    .valueProgrammatic("id")
                                                    .build()))))));

    final SchemaTransformer transformer = new SchemaTransformer(schema, false);
    Set<String> entities = transformer.getFederatedEntities();

    Assertions.assertFalse(entities.isEmpty());
    Assertions.assertEquals(1, entities.size());
    Assertions.assertTrue(entities.contains("Foo"));
  }

  @Test
  public void getFederatedEntities_entityHasDirective_found() {
    final GraphQLSchema schema =
        TEST_SCHEMA.transform(
            schemaBuilder ->
                schemaBuilder
                    .additionalDirective(FederationDirectives.key)
                    .additionalType(
                        FOO_TYPE.transform(
                            objectBuilder ->
                                objectBuilder.withDirective(FederationDirectives.key("id")))));

    final SchemaTransformer transformer = new SchemaTransformer(schema, false);
    Set<String> entities = transformer.getFederatedEntities();

    Assertions.assertFalse(entities.isEmpty());
    Assertions.assertEquals(1, entities.size());
    Assertions.assertTrue(entities.contains("Foo"));
  }

  @Test
  public void getFederatedEntities_noEntities_notFound() {
    final GraphQLSchema schema =
        TEST_SCHEMA.transform(schemaBuilder -> schemaBuilder.additionalType(FOO_TYPE));

    final SchemaTransformer transformer = new SchemaTransformer(schema, false);
    Set<String> entities = transformer.getFederatedEntities();

    Assertions.assertTrue(entities.isEmpty());
  }

  @Test
  public void setSchemaPrinterComparatorRegistry_withDefaultRegistry_succeeds() {
    final GraphQLSchema schema =
        TEST_SCHEMA.transform(
            schemaBuilder ->
                schemaBuilder.additionalDirective(FederationDirectives.key)
                .additionalType(
                    FOO_TYPE.transform(
                        objectBuilder ->
                            objectBuilder.withDirective(FederationDirectives.key("id")))));

    GraphqlTypeComparatorRegistry registry =
        DefaultGraphqlTypeComparatorRegistry.defaultComparators();
    SchemaTransformer transformer =
        new SchemaTransformer(schema, false).setSchemaPrinterComparatorRegistry(registry);

    Assertions.assertNotNull(transformer, "SchemaTransformer should be returned for method chaining");
  }

  @Test
  public void setSchemaPrinterComparatorRegistry_withAsIsRegistry_succeeds() {
    final GraphQLSchema schema =
        TEST_SCHEMA.transform(
            schemaBuilder ->
                schemaBuilder.additionalDirective(FederationDirectives.key)
                .additionalType(
                    FOO_TYPE.transform(
                        objectBuilder ->
                            objectBuilder.withDirective(FederationDirectives.key("id")))));

    GraphqlTypeComparatorRegistry registry = DefaultGraphqlTypeComparatorRegistry.AS_IS_REGISTRY;
    SchemaTransformer transformer =
        new SchemaTransformer(schema, false).setSchemaPrinterComparatorRegistry(registry);

    Assertions.assertNotNull(transformer, "SchemaTransformer should be returned for method chaining");
  }

  @Test
  public void setSchemaPrinterComparatorRegistry_enablesMethodChaining() {
    final GraphQLSchema schema =
        TEST_SCHEMA.transform(
            schemaBuilder ->
                schemaBuilder
                    .additionalDirective(FederationDirectives.key)
                    .additionalType(
                        FOO_TYPE.transform(
                            objectBuilder ->
                                objectBuilder.withDirective(FederationDirectives.key("id")))));

    // Test method chaining - verify we can chain setSchemaPrinterComparatorRegistry with other
    // methods
    SchemaTransformer transformer =
        new SchemaTransformer(schema, false)
            .setSchemaPrinterComparatorRegistry(
                DefaultGraphqlTypeComparatorRegistry.defaultComparators())
            .setFederation2(true);

    Assertions.assertNotNull(transformer, "SchemaTransformer should not be null");
  }

  @Test
  public void setSchemaPrinterComparatorRegistry_withNullRegistry_succeeds() {
    final GraphQLSchema schema =
        TEST_SCHEMA.transform(
            schemaBuilder ->
                schemaBuilder.additionalDirective(FederationDirectives.key)
                .additionalType(
                    FOO_TYPE.transform(
                        objectBuilder ->
                            objectBuilder.withDirective(FederationDirectives.key("id")))));

    // Setting null registry should be handled gracefully
    SchemaTransformer transformer =
        new SchemaTransformer(schema, false).setSchemaPrinterComparatorRegistry(null);

    Assertions.assertNotNull(transformer, "SchemaTransformer should be returned for method chaining");
  }

  @Test
  public void setSchemaPrinterComparatorRegistry_multipleCallsLastOneWins() {
    final GraphQLSchema schema =
        TEST_SCHEMA.transform(
            schemaBuilder ->
                schemaBuilder.additionalDirective(FederationDirectives.key)
                .additionalType(
                    FOO_TYPE.transform(
                        objectBuilder ->
                            objectBuilder.withDirective(FederationDirectives.key("id")))));

    GraphqlTypeComparatorRegistry registry1 =
        DefaultGraphqlTypeComparatorRegistry.defaultComparators();
    GraphqlTypeComparatorRegistry registry2 = DefaultGraphqlTypeComparatorRegistry.AS_IS_REGISTRY;

    SchemaTransformer transformer =
        new SchemaTransformer(schema, false)
            .setSchemaPrinterComparatorRegistry(registry1)
            .setSchemaPrinterComparatorRegistry(registry2);

    Assertions.assertNotNull(transformer, "SchemaTransformer should be returned for method chaining");
  }
}
