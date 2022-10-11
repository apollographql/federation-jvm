package com.apollographql.federation.graphqljava;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.apollographql.federation.graphqljava.data.Product;
import com.apollographql.federation.graphqljava.exceptions.MultipleFederationLinksException;
import com.apollographql.federation.graphqljava.exceptions.UnsupportedFederationVersionException;
import com.apollographql.federation.graphqljava.exceptions.UnsupportedLinkImportException;
import com.apollographql.federation.graphqljava.exceptions.UnsupportedRenameException;
import graphql.ExecutionResult;
import graphql.schema.DataFetcher;
import graphql.schema.FieldCoordinates;
import graphql.schema.GraphQLCodeRegistry;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLUnionType;
import graphql.schema.PropertyDataFetcher;
import graphql.schema.TypeResolver;
import graphql.schema.idl.RuntimeWiring;
import graphql.schema.idl.TypeRuntimeWiring;
import graphql.schema.idl.errors.SchemaProblem;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FederationTest {

  @Test
  public void verifyFederationV1Transformation() {
    verifyFederationTransformation("schemas/federationV1.graphql", false);
  }

  @Test
  public void verifyFederationV2Transformation() {
    verifyFederationTransformation("schemas/federationV2.graphql", true);
  }

  @Test
  public void verifyFederationTransformation_subgraphWithoutEntities() {
    verifyFederationTransformation("schemas/subgraphWithoutEntities.graphql", false);
  }

  @Test
  public void verifyFederationTransformation_subgraphWithEntitiesOnly() {
    verifyFederationTransformation("schemas/subgraphWithEntitiesOnly.graphql", false);
  }

  @Test
  public void verifyFederationTransformation_nonFederatedSchema_doNotRequireFederatedResolvers() {
    final String schemaSDL = FileUtils.readResource("schemas/subgraphWithoutEntities.graphql");
    assertDoesNotThrow(() -> Federation.transform(schemaSDL).build());
  }

  @Test
  public void verifyFederationTransformation_noEntityTypeResolver_throwsException() {
    final String schemaSDL = FileUtils.readResource("schemas/federationV2.graphql");
    assertThrows(
        SchemaProblem.class,
        () -> Federation.transform(schemaSDL).resolveEntityType(env -> null).build());
  }

  @Test
  public void verifyFederationTransformation_noEntitiesDataFetcher_throwsException() {
    final String schemaSDL = FileUtils.readResource("schemas/federationV2.graphql");
    assertThrows(
        SchemaProblem.class,
        () -> Federation.transform(schemaSDL).fetchEntities(env -> null).build());
  }

  @Test
  public void verifyFederationV2TransformationAndEntityResolution() {
    final String originalSDL = FileUtils.readResource("schemas/federationV2.graphql");

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
    final String sdl = FileUtils.readResource("schemas/federationV2.graphql");
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
  public void verifyFederationV2Transformation_withInterfaces() {
    final RuntimeWiring runtimeWiring =
        RuntimeWiring.newRuntimeWiring()
            .type(TypeRuntimeWiring.newTypeWiring("Product").typeResolver(env -> null).build())
            .build();
    final GraphQLSchema federatedSchema =
        verifyFederationTransformation("schemas/polymorphicSubgraph.graphql", runtimeWiring, true);
    final GraphQLUnionType entityType =
        (GraphQLUnionType) federatedSchema.getType(_Entity.typeName);
    assertNotNull(entityType, "Entity type should be defined");

    final Iterable<String> unionTypes =
        entityType.getTypes().stream()
            .map(GraphQLNamedType::getName)
            .sorted()
            .collect(Collectors.toList());

    assertIterableEquals(
        Arrays.asList("Book", "Movie"), unionTypes, "Entity union contains all expected types");
  }

  @Test
  public void verifyWeCannotRenameTagDirective() {
    assertThrows(
        UnsupportedRenameException.class,
        () -> verifyFederationTransformation("schemas/renamedTagImport.graphql", true));
  }

  @Test
  public void verifyWeCannotRenameInaccessibleDirective() {
    assertThrows(
        UnsupportedRenameException.class,
        () -> verifyFederationTransformation("schemas/renamedInaccessibleImport.graphql", true));
  }

  @Test
  public void verifyFederationV2Transformation_renames() {
    verifyFederationTransformation("schemas/renamedImports.graphql", true);
  }

  @Test
  public void verifyFederationV2Transformation_linkOnSchema() {
    verifyFederationTransformation("schemas/schemaImport.graphql", true);
  }

  @Test
  public void verifyFederationTransformation_composeDirective() {
    verifyFederationTransformation("schemas/composeDirective.graphql", true);
  }

  @Test
  public void
      verifyFederationV2Transformation_composeDirectiveFromUnsupportedVersion_throwsException() {
    final String schemaSDL =
        FileUtils.readResource("schemas/composeDirectiveUnsupportedSpecVersion.graphql");
    assertThrows(
        UnsupportedLinkImportException.class,
        () -> Federation.transform(schemaSDL).fetchEntities(env -> null).build());
  }

  @Test
  public void verifyFederationV2Transformation_unknownVersion_throwsException() {
    final String schemaSDL = FileUtils.readResource("schemas/unsupportedSpecVersion.graphql");
    assertThrows(
        UnsupportedFederationVersionException.class,
        () -> Federation.transform(schemaSDL).fetchEntities(env -> null).build());
  }

  @Test
  public void verifyFederationV2Transformation_multipleFedLinks_throwsException() {
    final String schemaSDL = FileUtils.readResource("schemas/multipleLinks.graphql");
    assertThrows(
        MultipleFederationLinksException.class,
        () -> Federation.transform(schemaSDL).fetchEntities(env -> null).build());
  }

  private GraphQLSchema verifyFederationTransformation(
      String schemaFileName, boolean isFederationV2) {
    final RuntimeWiring runtimeWiring = RuntimeWiring.newRuntimeWiring().build();
    return verifyFederationTransformation(schemaFileName, runtimeWiring, isFederationV2);
  }

  private GraphQLSchema verifyFederationTransformation(
      String schemaFileName, RuntimeWiring runtimeWiring, boolean isFederationV2) {
    final String baseFileName = schemaFileName.substring(0, schemaFileName.indexOf(".graphql"));

    final String originalSDL = FileUtils.readResource(schemaFileName);
    final GraphQLSchema federatedSchema =
        Federation.transform(originalSDL, runtimeWiring)
            .resolveEntityType(env -> null)
            .fetchEntities(entityFetcher -> null)
            .build();

    final String expectedFederatedSchemaSDL =
        FileUtils.readResource(baseFileName + "_federated.graphql");
    final String expectedServiceSDL;
    if (isFederationV2) {
      expectedServiceSDL = expectedFederatedSchemaSDL;
    } else {
      expectedServiceSDL = FileUtils.readResource(baseFileName + "_serviceSDL.graphql");
    }
    FederatedSchemaVerifier.verifySchemaSDL(
        federatedSchema, expectedFederatedSchemaSDL, isFederationV2);
    FederatedSchemaVerifier.verifySchemaContainsServiceFederationType(federatedSchema);
    FederatedSchemaVerifier.verifyServiceSDL(federatedSchema, expectedServiceSDL);
    return federatedSchema;
  }
}
