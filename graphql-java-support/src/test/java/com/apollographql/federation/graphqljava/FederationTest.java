package com.apollographql.federation.graphqljava;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apollographql.federation.graphqljava.data.Product;
import com.apollographql.federation.graphqljava.exceptions.MissingKeyException;
import com.apollographql.federation.graphqljava.exceptions.MultipleFederationLinksException;
import com.apollographql.federation.graphqljava.exceptions.UnsupportedFederationVersionException;
import com.apollographql.federation.graphqljava.exceptions.UnsupportedLinkImportException;
import com.apollographql.federation.graphqljava.exceptions.UnsupportedRenameException;
import graphql.ExecutionResult;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLSchema;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;
import graphql.schema.idl.errors.SchemaProblem;
import graphql.schema.validation.InvalidSchemaException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FederationTest {

  @Test
  public void verifyFederationV1Transformation() {
    verifyFederationTransformation("schemas/fedV1/schema.graphql");
  }

  @Test
  public void verifyFederationV2Transformation() {
    verifyFederationTransformation("schemas/fedV2/schema.graphql");
  }

  @Test
  public void verifyFederationTransformation_subgraphWithoutEntities() {
    verifyFederationTransformation("schemas/noEntities/schema.graphql");
  }

  @Test
  public void verifyFederationTransformation_subgraphWithEntitiesOnly() {
    verifyFederationTransformation("schemas/entitiesOnlySubgraph/schema.graphql");
  }

  @Test
  public void verifyFederationTransformation_nonFederatedSchema_doNotRequireFederatedResolvers() {
    final String schemaSDL = FileUtils.readResource("schemas/noEntities/schema.graphql");
    assertDoesNotThrow(() -> Federation.transform(schemaSDL).build());
  }

  @Test
  public void verifyFederationTransformation_noEntityTypeResolver_throwsException() {
    final String schemaSDL = FileUtils.readResource("schemas/fedV2/schema.graphql");
    assertThrows(
        SchemaProblem.class,
        () -> Federation.transform(schemaSDL).resolveEntityType(env -> null).build());
  }

  @Test
  public void verifyFederationTransformation_noEntitiesDataFetcher_throwsException() {
    final String schemaSDL = FileUtils.readResource("schemas/fedV2/schema.graphql");
    assertThrows(
        SchemaProblem.class,
        () -> Federation.transform(schemaSDL).fetchEntities(env -> null).build());
  }

  @Test
  public void verifyFederationV2TransformationAndEntityResolution() {
    final String originalSDL = FileUtils.readResource("schemas/fedV2/schema.graphql");

    @SuppressWarnings("rawtypes")
    DataFetcher entityDataFetcher =
        env -> {
          List<Map<String, Object>> representations = env.getArgument(_Entity.argumentName);
          return representations.stream()
              .map(
                  reference -> {
                    if ("Product".equals(reference.get("__typename"))) {
                      return Product.resolveReference(reference);
                    }
                    return null;
                  })
              .collect(Collectors.toList());
        };
    TypeResolver entityTypeResolver =
        env -> {
          final Object src = env.getObject();
          if (src instanceof Product) {
            return env.getSchema().getObjectType("Product");
          }
          return null;
        };

    final RuntimeWiring runtimeWiring =
        RuntimeWiring.newRuntimeWiring()
            .codeRegistry(
                GraphQLCodeRegistry.newCodeRegistry()
                    .dataFetcher(
                        FieldCoordinates.coordinates("Product", "package"),
                        PropertyDataFetcher.fetching("productPackage"))
                    .build())
            .build();

    final GraphQLSchema federatedSchema =
        Federation.transform(originalSDL, runtimeWiring)
            .resolveEntityType(entityTypeResolver)
            .fetchEntities(entityDataFetcher)
            .build();

    final ExecutionResult result =
        FederatedSchemaVerifier.execute(
            federatedSchema,
            "{\n"
                + "  _entities(representations: [{__typename:\"Product\", id: \"apollo-federation\"}]) {\n"
                + "    ... on Product { id sku package }\n"
                + "  }"
                + "}");
    assertEquals(0, result.getErrors().size(), "No errors");

    final Map<String, Object> data = result.getData();
    @SuppressWarnings("unchecked")
    final List<Map<String, Object>> _entities = (List<Map<String, Object>>) data.get("_entities");

    assertEquals(1, _entities.size());
    assertEquals("federation", _entities.get(0).get("sku"));
    assertEquals("@apollo/federation", _entities.get(0).get("package"));
  }

  @Test
  public void verifyFederationTransformation_noGlobalState() {
    // https://github.com/apollographql/federation-jvm/issues/7
    final String sdl = FileUtils.readResource("schemas/fedV2/schema.graphql");
    final GraphQLSchema first =
        Federation.transform(sdl)
            .resolveEntityType(env -> null)
            .fetchEntities(environment -> null)
            .build();

    final GraphQLSchema second =
        Federation.transform(sdl)
            .resolveEntityType(env -> null)
            .fetchEntities(environment -> null)
            .build();

    assertNotEquals(
        first,
        second,
        "Federation transformation generates different objects for each transformation");
  }

  @Test
  public void verifyFederationV2Transformation_polymorphicTypesMissingKey_throwsException() {
    final RuntimeWiring runtimeWiring =
        RuntimeWiring.newRuntimeWiring()
            .type(TypeRuntimeWiring.newTypeWiring("Product").typeResolver(env -> null).build())
            .build();
    assertThrows(
        MissingKeyException.class,
        () ->
            verifyFederationTransformation(
              "schemas/invalidPolymorphicSubgraphMissingKeys.graphql", runtimeWiring));
  }

  @Test
  public void verifyWeCannotRenameTagDirective() {
    assertThrows(
        UnsupportedRenameException.class,
        () -> verifyFederationTransformation("schemas/invalidRenameTagImport.graphql"));
  }

  @Test
  public void verifyWeCannotRenameInaccessibleDirective() {
    assertThrows(
        UnsupportedRenameException.class,
        () -> verifyFederationTransformation("schemas/invalidRenameInaccessibleImport.graphql"));
  }

  @Test
  public void verifyFederationV2Transformation_renames() {
    verifyFederationTransformation("schemas/renamedImports/schema.graphql");
  }

  @Test
  public void verifyFederationV2Transformation_linkOnSchema() {
    verifyFederationTransformation("schemas/schemaImport/schema.graphql");
  }

  @Test
  public void verifyFederationV2Transformation_composeDirective() {
    verifyFederationTransformation("schemas/composeDirective/schema.graphql");
  }

  @Test
  public void
      verifyFederationV2Transformation_composeDirectiveFromUnsupportedVersion_throwsException() {
    final String schemaSDL =
        FileUtils.readResource("schemas/invalidSpecVersionComposeDirective.graphql");
    assertThrows(
        UnsupportedLinkImportException.class,
        () -> Federation.transform(schemaSDL).fetchEntities(env -> null).build());
  }

  @Test
  public void verifyFederationV2Transformation_unknownVersion_throwsException() {
    final String schemaSDL = FileUtils.readResource("schemas/invalidSpecVersion.graphql");
    assertThrows(
        UnsupportedFederationVersionException.class,
        () -> Federation.transform(schemaSDL).fetchEntities(env -> null).build());
  }

  @Test
  public void verifyFederationV2Transformation_multipleFedLinks_throwsException() {
    final String schemaSDL = FileUtils.readResource("schemas/invalidMultipleFederationLinks.graphql");
    assertThrows(
        MultipleFederationLinksException.class,
        () -> Federation.transform(schemaSDL).fetchEntities(env -> null).build());
  }

  @Test
  public void verifySchemaCanBeExtended() {
    verifyFederationTransformation("schemas/extendSchema/schema.graphql");
  }

  @Test
  public void
      verifyFederationV2Transformation_multipleFedLinksSchemaAndExtension_throwsException() {
    final String schemaSDL = FileUtils.readResource("schemas/invalidMultipleFederationSchemaLinks.graphql");
    assertThrows(
        MultipleFederationLinksException.class,
        () -> Federation.transform(schemaSDL).fetchEntities(env -> null).build());
  }

  @Test
  public void verifyFederationV2Transformation_repeatableShareable() {
    verifyFederationTransformation("schemas/repeatableShareable/schema.graphql");
  }

  @Test
  public void
      verifyFederationV2Transformation_repeatableShareableFromUnsupportedVersion_throwsException() {
    final String schemaSDL =
        FileUtils.readResource("schemas/invalidSpecVersionRepeatableShareable.graphql");
    assertThrows(
        InvalidSchemaException.class,
        () ->
            Federation.transform(schemaSDL)
                .fetchEntities(env -> null)
                .resolveEntityType(env -> null)
                .build());
  }

  @Test
  public void verifyFederationV2Transformation_interfaceObject() {
    verifyFederationTransformation("schemas/interfaceObject/schema.graphql");
  }

  @Test
  public void
      verifyFederationV2Transformation_interfaceObjectFromUnsupportedVersion_throwsException() {
    final String schemaSDL =
        FileUtils.readResource("schemas/invalidSpecVersionInterfaceObject.graphql");
    assertThrows(
        UnsupportedLinkImportException.class,
        () ->
            Federation.transform(schemaSDL)
                .fetchEntities(env -> null)
                .resolveEntityType(env -> null)
                .build(),
        "foo");
  }

  @Test
  public void verifyFederationV2Transformation_interfaceEntity() {
    final RuntimeWiring runtimeWiring =
        RuntimeWiring.newRuntimeWiring()
            .type(TypeRuntimeWiring.newTypeWiring("Product").typeResolver(env -> null).build())
            .build();

    verifyFederationTransformation("schemas/interfaceEntity/schema.graphql", runtimeWiring);
  }

  @Test
  public void verifyFederationV2Transformation_nonResolvableKey_doesNotRequireResolvers() {
    final String originalSDL = FileUtils.readResource("schemas/nonResolvableKey/schema.graphql");
    final RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring().build();
    final GraphQLSchema federatedSchema = Federation.transform(originalSDL, runtimeWiring).build();

    final String expectedFullSchemaSDL =
        FileUtils.readResource("schemas/nonResolvableKey/schema_full.graphql");
    final String expectedFederatedSchemaSDL =
        FileUtils.readResource("schemas/nonResolvableKey/schema_federated.graphql");
    FederatedSchemaVerifier.verifyFullSchema(federatedSchema, expectedFullSchemaSDL);
    FederatedSchemaVerifier.verifySchemaContainsServiceFederationType(federatedSchema);
    FederatedSchemaVerifier.verifyServiceSDL(federatedSchema, expectedFederatedSchemaSDL);
  }

  @Test
  public void verifyFederationV2Transformation_authorization() {
    verifyFederationTransformation("schemas/authorization/schema.graphql");
  }

  @Test
  public void verifyFederationV2Transformation_customAuthenticated() {
    verifyFederationTransformation("schemas/customAuthenticated/schema.graphql");
  }

  @Test
  public void verifyFederationV2Transformation_authorizedFromUnsupportedVersion_throwsException() {
    final String schemaSDL =
        FileUtils.readResource("schemas/invalidSpecVersionAuthenticated.graphql");
    assertThrows(
        UnsupportedLinkImportException.class,
        () -> Federation.transform(schemaSDL).fetchEntities(env -> null).build());
  }

  @Test
  public void verifyFederationV2Transformation_policy() {
    verifyFederationTransformation("schemas/policy/schema.graphql");
  }

  @Test
  public void
      verifyFederationV2Transformation_requiresScopesFromUnsupportedVersion_throwsException() {
    final String schemaSDL =
        FileUtils.readResource("schemas/invalidSpecVersionRequiresScopes.graphql");
    assertThrows(
        UnsupportedLinkImportException.class,
        () -> Federation.transform(schemaSDL).fetchEntities(env -> null).build());
  }

  @Test
  public void verifyFederationV2Transformation_policyFromUnsupportedVersion_throwsException() {
    final String schemaSDL = FileUtils.readResource("schemas/invalidSpecVersionPolicy.graphql");
    assertThrows(
        UnsupportedLinkImportException.class,
        () -> Federation.transform(schemaSDL).fetchEntities(env -> null).build());
  }

  @Test
  public void verifyFederationV2Transformation_progressiveOverride() {
    verifyFederationTransformation("schemas/progressiveOverride/schema.graphql");
  }

  @Test
  public void
      verifyFederationV2Transformation_progressiveOverrideFromUnsupportedVersion_throwsException() {
    final String schemaSDL =
        FileUtils.readResource("schemas/invalidSpecVersionProgressiveOverride.graphql");
    assertThrows(
        SchemaProblem.class,
        () -> Federation.transform(schemaSDL).fetchEntities(env -> null).build());
  }

  @Test
  public void verifyFederationV2Transformation_scalarsDefinedInSchemaButNotWired() {
    verifyFederationTransformation("schemas/scalars/schema.graphql");
  }

  private GraphQLSchema verifyFederationTransformation(String schemaFileName) {
    final RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring().build();
    return verifyFederationTransformation(schemaFileName, runtimeWiring);
  }

  private GraphQLSchema verifyFederationTransformation(
      String schemaFileName, RuntimeWiring runtimeWiring) {
    final String baseFileName = schemaFileName.substring(0, schemaFileName.indexOf(".graphql"));

    final String originalSDL = FileUtils.readResource(schemaFileName);
    final GraphQLSchema federatedSchema =
        Federation.transform(originalSDL, runtimeWiring)
            .resolveEntityType(env -> null)
            .fetchEntities(entityFetcher -> null)
            .build();

    final String expectedFullSchemaSDL = FileUtils.readResource(baseFileName + "_full.graphql");
    final String expectedFederatedSchemaSDL =
        FileUtils.readResource(baseFileName + "_federated.graphql");

    FederatedSchemaVerifier.verifyFullSchema(federatedSchema, expectedFullSchemaSDL);
    FederatedSchemaVerifier.verifySchemaContainsServiceFederationType(federatedSchema);
    FederatedSchemaVerifier.verifyServiceSDL(federatedSchema, expectedFederatedSchemaSDL);
    return federatedSchema;
  }
}
