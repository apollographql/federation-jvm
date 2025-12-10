package com.apollographql.federation.graphqljava;

import static org.junit.jupiter.api.Assertions.*;

import graphql.language.DirectiveDefinition;
import graphql.language.SDLNamedDefinition;
import graphql.schema.GraphQLSchema;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class Federation2_12Test {

  @Test
  void testFederation2_12SpecLoading() {
    // Test that Federation v2.12 spec definitions can be loaded
    List<SDLNamedDefinition> definitions =
        FederationDirectives.loadFederationSpecDefinitions(Federation.FEDERATION_SPEC_V2_12);

    assertNotNull(definitions);
    assertFalse(definitions.isEmpty());

    // Check that @cacheTag directive is present
    Optional<DirectiveDefinition> cacheTagDirective =
        definitions.stream()
            .filter(def -> def instanceof DirectiveDefinition)
            .map(def -> (DirectiveDefinition) def)
            .filter(directive -> "cacheTag".equals(directive.getName()))
            .findFirst();

    assertTrue(
        cacheTagDirective.isPresent(), "@cacheTag directive should be present in Federation v2.12");

    DirectiveDefinition cacheTag = cacheTagDirective.get();
    assertEquals("cacheTag", cacheTag.getName());
    assertTrue(cacheTag.isRepeatable(), "@cacheTag should be repeatable");

    // Check that it has the 'format' argument
    assertTrue(
        cacheTag.getInputValueDefinitions().stream()
            .anyMatch(arg -> "format".equals(arg.getName())),
        "@cacheTag should have a 'format' argument");
  }

  @Test
  void testSchemaWithCacheTag() {
    // Test that a schema with @cacheTag can be parsed and transformed
    String schemaString =
        "extend schema\n"
            + "  @link(url: \"https://specs.apollo.dev/federation/v2.12\", import: [\"@key\", \"@cacheTag\"])\n"
            + "\n"
            + "type Product @key(fields: \"id\") @cacheTag(format: \"product-{$key.id}\") {\n"
            + "  id: ID!\n"
            + "  name: String!\n"
            + "  price: Float! @cacheTag(format: \"price-{$key.id}\")\n"
            + "}\n"
            + "\n"
            + "type Query {\n"
            + "  product(id: ID!): Product @cacheTag(format: \"product-query\")\n"
            + "}\n";

    // This should not throw an exception - Federation.transform handles directive definitions
    assertDoesNotThrow(
        () -> {
          // Use Federation.transform which handles importing directives from @link
          SchemaTransformer transformer =
              Federation.transform(
                      schemaString,
                      graphql.schema.idl.RuntimeWiring.newRuntimeWiring()
                          .type("Query", builder -> builder.dataFetcher("product", env -> null))
                          .build())
                  .fetchEntities(env -> null)
                  .resolveEntityType(env -> env.getSchema().getObjectType("Product"));

          GraphQLSchema federatedSchema = transformer.build();
          assertNotNull(federatedSchema);
        });
  }

  @Test
  void testBackwardsCompatibility() {
    // Ensure that all previous federation versions still work
    String[] versions = {
      Federation.FEDERATION_SPEC_V2_0,
      Federation.FEDERATION_SPEC_V2_1,
      Federation.FEDERATION_SPEC_V2_2,
      Federation.FEDERATION_SPEC_V2_3,
      Federation.FEDERATION_SPEC_V2_5,
      Federation.FEDERATION_SPEC_V2_6,
      Federation.FEDERATION_SPEC_V2_7,
      Federation.FEDERATION_SPEC_V2_8,
      Federation.FEDERATION_SPEC_V2_9,
      Federation.FEDERATION_SPEC_V2_12
    };

    for (String version : versions) {
      assertDoesNotThrow(
          () -> {
            List<SDLNamedDefinition> definitions =
                FederationDirectives.loadFederationSpecDefinitions(version);
            assertNotNull(definitions, "Definitions should not be null for version: " + version);
            assertFalse(
                definitions.isEmpty(), "Definitions should not be empty for version: " + version);
          },
          "Failed to load federation spec for version: " + version);
    }
  }
}
